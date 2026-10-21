#!/usr/bin/env python3
"""Compare persisted Phase11A snapshots. Never grants practical acceptance.

Exit 0: exact contract/identity/file checks pass; 1: integrity difference;
2: missing/malformed evidence. No value tolerance, no ignored unknown contract fields.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

RUN = re.compile(r"P11A-\d{4}-\d{2}-\d{2}-[A-Za-z0-9-]+")
COLLECTIONS = {
    "properties": "propertyId", "loans": "id", "logbookTrips": "id",
    "managedDocuments": "documentId", "bankAccounts": "accountId",
    "bankTransactions": "transactionId", "bankReceiptLinks": "linkId",
    "bankRentAssignments": "assignmentId", "bankLoanAssignments": "assignmentId",
    "exportAuditRuns": "exportlaufId", "standardRoutes": "id",
    "bankLearningRules": "ruleId", "bankRuleEvidence": "evidenceId",
    "bankRecurringPatterns": "patternId",
}


def canonical(value):
    # Entity arrays are sets in the unchanged Kotlin contract. Chain ranks remain object keys.
    if isinstance(value, dict):
        return {k: canonical(v) for k, v in sorted(value.items())}
    if isinstance(value, list):
        return sorted((canonical(v) for v in value), key=lambda v: json.dumps(v, sort_keys=True))
    return value


def diff(a, b, path=""):
    if type(a) is not type(b):
        return [path]
    if isinstance(a, dict):
        return [p for k in sorted(a.keys() | b.keys()) for p in
                ([f"{path}/{k}"] if k not in a or k not in b else diff(a[k], b[k], f"{path}/{k}"))]
    if isinstance(a, list):
        return [path] if len(a) != len(b) else [p for i, (x, y) in enumerate(zip(a, b)) for p in diff(x, y, f"{path}/{i}")]
    return [] if a == b else [path]


def inspect(snapshot, units, errors, label, post_import_reference=False):
    def check(ok, message):
        if not ok:
            errors.append(f"{label}: {message}")

    assert snapshot["schemaVersion"] == 1, "unsupported snapshot schema"
    meta = snapshot["meta"]
    assert RUN.fullmatch(meta["runId"]), "invalid run ID"
    assert re.fullmatch(r"[0-9a-f]{40}", meta["commit"]), "missing app commit"
    for field in ("capturedAt", "device", "android", "appVersion", "source", "label"):
        assert isinstance(meta[field], str) and meta[field], f"missing meta/{field}"
    raw, room, contract = snapshot["raw"], snapshot["roomRaw"], snapshot["contract"]
    assert set(contract) == {"authoritativeFields", "normalizedFields", "reconstructedFields"}
    assert {"supplemental", "receipts", "units", "afa", "persistedRoom"} <= set(contract["authoritativeFields"])
    assert {"receiptOriginalOrder", "unitNameBindings", "roomOriginalOrder"} <= set(contract["reconstructedFields"])
    assert "receiptGrossAmountCent" in contract["normalizedFields"]
    supplemental = raw["supplemental"]
    collections = {key: supplemental[key] for key in COLLECTIONS}
    collections.update(receipts=raw["receipts"], roomReceipts=room["receipts"], roomDocuments=room["managedDocuments"],
                       tenancies=snapshot["tenancies"], afa=raw["afa"], originals=snapshot["originals"])
    collections.update(belege=snapshot["secondaryRoom"]["belege"], receiptEntities=snapshot["secondaryRoom"]["receiptEntities"])
    collections["units"] = [dict(u, propertyId=p["propertyId"]) for p in raw["units"] for u in p["units"]]
    id_fields = dict(COLLECTIONS, receipts="internalId", roomReceipts="internalId", roomDocuments="documentId",
                     units="unitId", tenancies="id", afa="propertyId", originals="documentId", belege="id", receiptEntities="id")
    ids = {}
    for key, rows in collections.items():
        assert isinstance(rows, list) and all(isinstance(row, dict) for row in rows), f"invalid collection {key}"
        values = [row[id_fields[key]] for row in rows]
        check(all(v is not None and str(v).strip() for v in values), f"missing IDs: {key}")
        # Tenant IDs are scoped by property/unit; retain the actual ID too.
        if key == "tenancies":
            values = [(r["propertyId"], r["unitId"], r["id"]) for r in rows]
        check(len(values) == len(set(values)), f"duplicate IDs: {key}")
        ids[key] = sorted(values, key=str)
    receipts = {r["internalId"]: r for r in collections["receipts"]}
    receipt_numbers = {r["id"]: r for r in collections["receipts"]}
    check(len(receipt_numbers) == len(receipts), "duplicate numeric receipt IDs")
    check(all(isinstance(v, int) and not isinstance(v, bool) and v > 0 for v in receipt_numbers), "missing/invalid numeric receipt IDs")
    check(set(ids["roomReceipts"]) == set(receipts), "Room/receipt IDs disagree (including tombstones)")
    check(ids["managedDocuments"] == ids["roomDocuments"], "Room/document IDs disagree")
    full_receipts = {r["id"]: r for r in room["receipts"]}
    for key, path_field in (("belege", "bildPfad"), ("receiptEntities", "pdfPath")):
        check(set(ids[key]) == set(receipt_numbers), f"missing/orphan secondary receipt IDs: {key}")
        for row in collections[key]:
            parent = full_receipts.get(row["id"])
            check(parent is not None and row["betrag"] == parent["bruttobetrag"], f"secondary amount mismatch: {key}/{row['id']}")
            check(parent is not None and row[path_field] == parent["imageUrl"], f"secondary original path mismatch: {key}/{row['id']}")
    properties = set(ids["properties"])
    scopes = {(u["propertyId"], u["unitId"]) for u in collections["units"]}
    accounts, transactions = set(ids["bankAccounts"]), set(ids["bankTransactions"])
    for key, rows in collections.items():
        for row in rows:
            pid, uid = row.get("propertyId"), row.get("unitId")
            if pid:
                check(pid in properties, f"orphan property: {key}/{pid}")
            if uid:
                check((pid, uid) in scopes, f"orphan/wrong unit scope: {key}/{uid}")
            if row.get("accountId"):
                check(row["accountId"] in accounts, f"orphan account: {key}")
            if row.get("transactionId"):
                check(row["transactionId"] in transactions, f"orphan transaction: {key}")
            if row.get("loanId"):
                check(row["loanId"] in ids["loans"], f"orphan loan: {key}")
            for field in ("receiptId", "sourceReceiptId", "expenseReceiptId"):
                if row.get(field) is not None:
                    check(row[field] in receipt_numbers, f"orphan {field}: {key}")
            if row.get("receiptInternalId"):
                parent = receipts.get(row["receiptInternalId"])
                check(parent is not None, f"orphan receiptInternalId: {key}")
                if parent:
                    if pid:
                        check((parent.get("propertyId"), parent.get("unitId") or "") == (pid, uid or ""), f"receipt scope mismatch: {key}")
                    if row.get("receiptId") is not None:
                        check(parent["id"] == row["receiptId"], f"receipt identities disagree: {key}")
    documents = {r["documentId"]: r for r in collections["managedDocuments"]}
    physical = {d["documentId"] for d in documents.values() if d["fileSizeBytes"] > 0 and d["sha256"]}
    check(physical == set(ids["originals"]), "missing/unexpected original evidence")
    for item in collections["originals"]:
        doc = documents.get(item["documentId"])
        check(item["readable"] is True, f"original unreadable: {item['documentId']}")
        check(bool(re.fullmatch(r"[0-9a-f]{64}", item["sha256"])), "invalid original SHA256")
        if doc:
            check(item["sha256"] == item["expectedSha256"] == doc["sha256"], f"original hash: {item['documentId']}")
            check(item["size"] == item["expectedSize"] == doc["fileSizeBytes"], f"original size: {item['documentId']}")
            check(item["mime"] == doc["mimeType"] and item["filename"] == doc["originalFilename"], "original MIME/filename")
    chains = room["receiptOriginalChains"]
    check(set(chains) == set(receipts), "missing original-chain receipt evidence")
    chained_documents = []
    for internal_id, chain in chains.items():
        check(set(chain) == {str(i) for i in range(len(chain))}, f"invalid chain ranks: {internal_id}")
        for item in chain.values():
            doc = documents.get(item["documentId"])
            chained_documents.append(item["documentId"])
            check(doc is not None and item["readable"] is True and item["sha256"] == (doc or {}).get("sha256")
                  and doc.get("receiptInternalId") == internal_id, f"unresolved/wrong-parent chain: {internal_id}")
    attached_originals = {d["documentId"] for d in documents.values()
                          if d["documentId"] in physical and d.get("receiptInternalId")}
    check(set(chained_documents) == attached_originals and len(chained_documents) == len(attached_originals), "missing/duplicate original chain members")
    expected = dict(properties=5, units=units, receipts=70, roomReceipts=70, managedDocuments=70,
                    roomDocuments=70, bankTransactions=120, bankAccounts=1, bankReceiptLinks=1,
                    bankRentAssignments=6, loans=1, logbookTrips=10, originals=60, tenancies=units, afa=5,
                    exportAuditRuns=100, belege=70, receiptEntities=70)
    counts = {k: len(v) for k, v in collections.items()}
    for key, count in counts.items():
        check(snapshot["counts"][key] == count, f"declared count disagrees with actual records: {key}")
    for key, count in expected.items():
        extra_imports = post_import_reference and key in {"receipts", "roomReceipts", "managedDocuments", "roomDocuments", "originals", "belege", "receiptEntities"}
        check(counts[key] >= count if extra_imports else counts[key] == count,
              f"count {key}: {counts[key]} {'<' if extra_imports else '!='} {count}")
    return ids, counts


def compare(before, after, units, post_import_reference=False):
    errors = []
    a_ids, a_counts = inspect(before, units, errors, "baseline", post_import_reference)
    b_ids, b_counts = inspect(after, units, errors, "after", post_import_reference)
    for field in ("runId", "commit", "source"):
        if before["meta"][field] != after["meta"][field]:
            errors.append(f"meta/{field} differs")
    missing = {k: sorted(set(a_ids[k]) - set(b_ids[k]), key=str) for k in a_ids}
    new = {k: sorted(set(b_ids[k]) - set(a_ids[k]), key=str) for k in a_ids}
    if any(missing.values()) or any(new.values()):
        errors.append("missing/unexpected IDs")
    contract_diff = diff(canonical(before["contract"]), canonical(after["contract"]))
    # Independently compare authoritative amounts, areas, dates, rent data, MIME and relations,
    # even if someone accidentally supplies a stale precomputed contract.
    business_diff = diff(canonical(before["raw"]), canonical(after["raw"]))
    room_raw_diff = diff(canonical(before["roomRaw"]), canonical(after["roomRaw"]))
    secondary_raw_diff = diff(canonical(before["secondaryRoom"]), canonical(after["secondaryRoom"]))
    original_diff = diff(canonical(before["originals"]), canonical(after["originals"]))
    tenant_diff = diff(canonical(before["tenancies"]), canonical(after["tenancies"]))
    def secondary_contract(snapshot):
        # Derived copies must match primary RAW (checked above). Reuse the exact
        # amount representation already accepted by the sole Kotlin contract.
        canonical_receipts = {r["id"]: r for r in snapshot["contract"]["authoritativeFields"]["persistedRoom"]["receipts"]}
        result = {}
        for key, path_field in (("belege", "bildPfad"), ("receiptEntities", "pdfPath")):
            result[key] = []
            for row in snapshot["secondaryRoom"][key]:
                copy = dict(row)
                copy.pop(path_field)
                copy["betrag"] = canonical_receipts.get(row["id"], {}).get("bruttobetrag", row["betrag"])
                result[key].append(copy)
        return canonical(result)
    secondary_diff = diff(secondary_contract(before), secondary_contract(after))
    strict_business = []
    for key in ("receipts", "units", "afa"):
        strict_business += diff(canonical(before["raw"][key]), canonical(after["raw"][key]), "/raw/" + key)
    for key in COLLECTIONS:
        if key != "managedDocuments":
            strict_business += diff(canonical(before["raw"]["supplemental"][key]),
                                    canonical(after["raw"]["supplemental"][key]), "/supplemental/" + key)
    # Raw-only differences stay reviewable. The sole Kotlin Phase10D contract judges C/D fields.
    if contract_diff or original_diff or tenant_diff or strict_business or secondary_diff:
        errors.append("contract/original/tenancy differences")
    return dict(ok=not errors, status="AUTOMATISIERT BESTANDEN" if not errors else "NOCH OFFEN",
                practicalStatus="NOCH OFFEN", comparisonMode="POST_IMPORT_REFERENCE" if post_import_reference else "PHASE10B_PORTFOLIO", runId=before["meta"]["runId"], errors=errors,
                baselineCounts=a_counts, afterCounts=b_counts, missingIds=missing, unexpectedIds=new,
                relationErrors=[e for e in errors if "orphan" in e or "scope mismatch" in e or "identities disagree" in e],
                duplicateErrors=[e for e in errors if "duplicate" in e],
                originalHashesMatched=sum(any(a["documentId"] == b["documentId"] and a["sha256"] == b["sha256"]
                    and a["size"] == b["size"] and a["readable"] is True and b["readable"] is True
                    for b in after["originals"]) for a in before["originals"]),
                contractDifferenceCount=len(contract_diff), contractDifferences=contract_diff, rawDifferences=business_diff,
                roomRawDifferences=room_raw_diff, secondaryRawDifferences=secondary_raw_diff,
                originalDifferences=original_diff, tenancyDifferences=tenant_diff, businessDifferences=strict_business, secondaryDifferences=secondary_diff)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--units", type=int, choices=(17, 21), required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--post-import-reference", action="store_true",
                        help="Compare an explicitly documented post-picker state to its restart snapshot; does not approve initial import changes. IDs, all values and hashes remain exact; original portfolio minimum counts remain required.")
    args = parser.parse_args()
    try:
        paths = (args.baseline, args.after)
        snapshots = [json.loads(p.read_text(), parse_constant=lambda _: (_ for _ in ()).throw(ValueError("nonfinite JSON"))) for p in paths]
        result = compare(*snapshots, args.units, args.post_import_reference)
        result["inputs"] = [dict(name=p.name, sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths]
        code = 0 if result["ok"] else 1
    except (OSError, ValueError, KeyError, TypeError, AssertionError) as error:
        result = dict(ok=False, status="NOCH OFFEN", practicalStatus="NOCH OFFEN", evidenceError=str(error))
        code = 2
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({k: v for k, v in result.items() if k in ("ok", "status", "runId", "errors", "evidenceError")}, ensure_ascii=False))
    return code


if __name__ == "__main__":
    sys.exit(main())

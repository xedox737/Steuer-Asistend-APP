import copy
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "phase11a-integrity-check.py"
spec = importlib.util.spec_from_file_location("integrity", SCRIPT)
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


def fixture():
    props = [dict(propertyId=f"p{i}") for i in range(5)]
    units = [dict(propertyId=f"p{i}", units=[dict(unitId=f"u{i}-{j}", area=50, rent=900, start="2020-01-01") for j in range(count)]) for i, count in enumerate((8, 6, 1, 1, 1))]
    receipts = [dict(id=i+1, internalId=f"r{i}", propertyId="p0", unitId="u0-0", cent=125) for i in range(70)]
    docs = [dict(documentId=f"d{i}", receiptInternalId=f"r{i}", propertyId="p0", unitId="u0-0",
                 fileSizeBytes=4 if i < 60 else 0, sha256="a" * 64 if i < 60 else "", mimeType="application/pdf", originalFilename=f"{i}.pdf") for i in range(70)]
    originals = [dict(documentId=f"d{i}", receiptInternalId=f"r{i}", sha256="a" * 64, expectedSha256="a" * 64,
                      size=4, expectedSize=4, readable=True, mime="application/pdf", filename=f"{i}.pdf") for i in range(60)]
    supplemental = {k: [] for k in qa.COLLECTIONS}
    supplemental.update(properties=props, managedDocuments=docs, loans=[dict(id=71, propertyId="p0", amount=1000)],
                        bankAccounts=[dict(accountId="a")], bankTransactions=[dict(transactionId=f"t{i}", accountId="a", amount=100) for i in range(120)],
                        bankReceiptLinks=[dict(linkId="l", transactionId="t0", receiptId=1, receiptInternalId="r0")],
                        bankRentAssignments=[dict(assignmentId=f"a{i}", transactionId=f"t{i}", propertyId="p0", unitId="u0-0") for i in range(6)],
                        logbookTrips=[dict(id=i, propertyId="p0", sourceReceiptId=i+1, expenseReceiptId=i+11) for i in range(10)],
                        exportAuditRuns=[dict(exportlaufId=f"e{i}") for i in range(100)])
    room = dict(receipts=[dict(r, bruttobetrag=1.25, imageUrl="/test/original") for r in receipts], managedDocuments=copy.deepcopy(docs),
                receiptOriginalChains={f"r{i}": {"0": dict(documentId=f"d{i}", sha256="a"*64, readable=True)} if i < 60 else {} for i in range(70)})
    raw = dict(receipts=receipts, supplemental=supplemental, units=units, afa=[dict(propertyId=f"p{i}", basis=200000) for i in range(5)])
    contract = dict(authoritativeFields=dict(copy.deepcopy(raw), persistedRoom=copy.deepcopy(room)),
                    normalizedFields=dict(receiptGrossAmountCent={}),
                    reconstructedFields=dict(receiptOriginalOrder={}, unitNameBindings={}, roomOriginalOrder={}))
    secondary = dict(belege=[dict(id=r["id"], betrag=1.25, bildPfad="/test/original") for r in receipts],
                     receiptEntities=[dict(id=r["id"], betrag=1.25, pdfPath="/test/original") for r in receipts])
    snapshot = dict(secondaryRoom=secondary, schemaVersion=1, meta=dict(runId="P11A-2026-10-20-TEST", commit="a"*40, capturedAt="2026-10-20T00:00:00Z",
                device="synthetic unit fixture", android="35", appVersion="1", source="UNIT_FIXTURE", label="baseline"),
                raw=raw, roomRaw=room, contract=contract, originals=originals,
                tenancies=[dict(id=j, propertyId=p["propertyId"], unitId=u["unitId"], rent=900) for j, (p, u) in
                           enumerate((p, u) for p in units for u in p["units"])])
    snapshot["counts"] = {k:len(v) for k, v in supplemental.items()}
    snapshot["counts"].update(receipts=70, roomReceipts=70, roomDocuments=70, units=17, afa=5,
                              tenancies=17, originals=60, belege=70, receiptEntities=70)
    return snapshot


class IntegrityTests(unittest.TestCase):
    def setUp(self):
        self.before = fixture()
        self.after = copy.deepcopy(self.before)

    def result(self):
        return qa.compare(self.before, self.after, 17)

    def test_identical_complete_snapshot_is_automated_only(self):
        result = self.result()
        self.assertTrue(result["ok"], result)
        self.assertEqual("AUTOMATISIERT BESTANDEN", result["status"])
        self.assertEqual("NOCH OFFEN", result["practicalStatus"])

    def test_removed_unexpected_duplicate_and_empty_ids(self):
        for mutation in (lambda r: r.pop(), lambda r: r.append(dict(r[0], internalId="new", id=999)),
                         lambda r: r.append(copy.deepcopy(r[0])), lambda r: r[0].update(internalId="")):
            with self.subTest(mutation=mutation):
                self.after = copy.deepcopy(self.before)
                mutation(self.after["raw"]["receipts"])
                self.assertFalse(self.result()["ok"])

    def test_orphans_and_cross_property_unit(self):
        for key, field, value in (("logbookTrips", "sourceReceiptId", 999), ("bankTransactions", "accountId", "missing"),
                                  ("bankRentAssignments", "propertyId", "p1"), ("managedDocuments", "receiptInternalId", "missing")):
            with self.subTest(key=key):
                self.after = copy.deepcopy(self.before)
                self.after["raw"]["supplemental"][key][0][field] = value
                self.assertFalse(self.result()["ok"])

    def test_changed_amount_area_rent_date_even_with_stale_contract(self):
        for path, field in (("receipts", "cent"), ("units", "area"), ("units", "rent"), ("units", "start")):
            with self.subTest(field=field):
                self.after = copy.deepcopy(self.before)
                row = self.after["raw"][path][0]
                if path == "units":
                    row = row["units"][0]
                row[field] = "changed"
                self.assertFalse(self.result()["ok"])

    def test_hash_mime_missing_original_and_unreadable_chain(self):
        for field, value in (("sha256", "b"*64), ("mime", "image/png"), ("readable", False), ("size", 99)):
            with self.subTest(field=field):
                self.after = copy.deepcopy(self.before)
                self.after["originals"][0][field] = value
                self.assertFalse(self.result()["ok"])
        self.after = copy.deepcopy(self.before)
        self.after["originals"].pop()
        self.assertFalse(self.result()["ok"])
        self.after = copy.deepcopy(self.before)
        self.after["roomRaw"]["receiptOriginalChains"]["r0"]["0"]["readable"] = False
        self.assertFalse(self.result()["ok"])

    def test_unknown_authoritative_contract_field_remains_strict(self):
        self.after["contract"]["authoritativeFields"]["futureField"] = "changed"
        self.assertFalse(self.result()["ok"])

    def test_tenancy_id_and_afa_id_change(self):
        self.after["tenancies"][0]["id"] = 1000
        self.assertFalse(self.result()["ok"])
        self.after = copy.deepcopy(self.before)
        self.after["raw"]["afa"][0]["propertyId"] = "missing"
        self.assertFalse(self.result()["ok"])

    def test_run_and_commit_mix_rejected(self):
        for field, value in (("runId", "P11A-2026-10-20-OTHER"), ("commit", "b"*40)):
            self.after = copy.deepcopy(self.before)
            self.after["meta"][field] = value
            self.assertFalse(self.result()["ok"])

    def test_incomplete_or_empty_evidence_rejected(self):
        for data in ({}, dict(self.before, contract={}), dict(self.before, raw={})):
            with self.assertRaises((AssertionError, KeyError)):
                qa.compare(data, data, 17)

    def test_secondary_beleg_ids_amounts_and_original_paths(self):
        self.after["secondaryRoom"]["belege"][0]["id"] = 999
        self.assertFalse(self.result()["ok"])
        self.after = copy.deepcopy(self.before)
        self.after["secondaryRoom"]["receiptEntities"][0]["betrag"] = 999
        self.assertFalse(self.result()["ok"])
        self.after = copy.deepcopy(self.before)
        self.after["secondaryRoom"]["belege"][0]["bildPfad"] = "/wrong"
        self.assertFalse(self.result()["ok"])

    def test_post_import_reference_keeps_default_portfolio_and_id_checks_strict(self):
        state = copy.deepcopy(self.before)
        doc = dict(state["raw"]["supplemental"]["managedDocuments"][0], documentId="picker-doc", receiptInternalId=None,
                   originalFilename="picker.pdf", sha256="b"*64)
        state["raw"]["supplemental"]["managedDocuments"].append(doc)
        state["roomRaw"]["managedDocuments"].append(copy.deepcopy(doc))
        state["originals"].append(dict(state["originals"][0], documentId="picker-doc", receiptInternalId=None,
                                      filename="picker.pdf", sha256="b"*64, expectedSha256="b"*64))
        state["counts"].update(managedDocuments=71, roomDocuments=71, originals=61)
        state["contract"]["authoritativeFields"]["supplemental"]["managedDocuments"].append(copy.deepcopy(doc))
        state["contract"]["authoritativeFields"]["persistedRoom"]["managedDocuments"].append(copy.deepcopy(doc))
        self.assertFalse(qa.compare(state, state, 17)["ok"])
        self.assertFalse(qa.compare(self.before, state, 17, True)["ok"])
        result = qa.compare(state, copy.deepcopy(state), 17, True)
        self.assertTrue(result["ok"], result)
        self.assertEqual("NOCH OFFEN", result["practicalStatus"])
        changed = copy.deepcopy(state)
        changed["originals"][-1]["sha256"] = "c"*64
        self.assertFalse(qa.compare(state, changed, 17, True)["ok"])

    def test_entity_order_is_not_identity(self):
        self.after["raw"]["receipts"].reverse()
        self.after["contract"]["authoritativeFields"]["receipts"].reverse()
        self.assertTrue(self.result()["ok"])


if __name__ == "__main__":
    unittest.main()

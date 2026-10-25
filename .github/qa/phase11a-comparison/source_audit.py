#!/usr/bin/env python3
"""Read-only source comparison at pinned commits; code equality is not execution evidence."""
import difflib
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

COMMITS = {"main": "69f35772b6480a958ca7479b024dbd4d8bd42fa1", "test": "8ddd556061d6d9a5ed607163aff42dbf3578f8c1"}
FILES = ["data/DrivePersistenceRepository.kt", "api/GoogleDriveClient.kt", "data/DocumentModels.kt",
         "data/SupplementalDriveBackup.kt", "data/ManagedDocumentService.kt", "data/ReceiptDatabase.kt", "ui/ReceiptViewModel.kt"]

def audit(destination):
    destination.mkdir(parents=True, exist_ok=True)
    result = {"commits": COMMITS, "files": {}, "blocks": {}}
    for short in FILES + ["app/build.gradle.kts"]:
        path = short if short.startswith("app/") else "app/src/main/java/com/example/" + short
        values = {k: subprocess.check_output(["git", "show", sha + ":" + path]).decode() for k, sha in COMMITS.items()}
        result["files"][path] = {k: hashlib.sha256(v.encode()).hexdigest() for k, v in values.items()}
        result["files"][path]["identical"] = values["main"] == values["test"]
        (destination / (Path(path).name + ".diff")).write_text("".join(difflib.unified_diff(
            values["main"].splitlines(True), values["test"].splitlines(True), fromfile="main/"+path, tofile="test/"+path)))
        if not short.endswith("DrivePersistenceRepository.kt"):
            continue
        regions = {
            "receiptManagedConstructor": ("            val receiptManagedDocument = ManagedDocument(", "            localRepository.upsertManagedDocument(receiptManagedDocument)"),
            "receiptMissingSource": ('            } else {\n                val updatedReceipt = currentReceipt.copy(\n                    syncStatus = "ERROR",\n                    syncError = "Keine lokale Quelldatei gefunden oder Datei ist leer."', "            val existingDocs ="),
            "receiptSync": ("    suspend fun syncReceiptToDrive(", "    suspend fun checkDriveInventoryForRestore(")}
        for name, (start, end) in regions.items():
            blocks = {}
            lines = {}
            for k, v in values.items():
                a = v.index(start)
                b = v.index(end, a)
                blocks[k] = v[a:b]
                lines[k] = v[:a].count("\n") + 1
                (destination / (name+"-"+k+".kt.txt")).write_text(blocks[k])
            normalized = {k: v.replace("DriveEnvironment.appIdentifier", '"ImmobilienBelegApp"') for k, v in blocks.items()}
            result["blocks"][name] = {"byteIdentical": blocks["main"] == blocks["test"],
                "identicalAfterNamespaceTagNormalization": normalized["main"] == normalized["test"], "lines": lines,
                "sha256": {k: hashlib.sha256(v.encode()).hexdigest() for k, v in blocks.items()}}
    (destination / "source-comparison.json").write_text(json.dumps(result, indent=2) + "\n")
    return result

if __name__ == "__main__":
    result = audit(Path(sys.argv[1]))
    print(json.dumps(result["blocks"], indent=2))

from pathlib import Path

path = Path("app/src/main/java/com/example/data/DrivePersistenceRepository.kt")
text = path.read_text(encoding="utf-8")
old = '''                    localRepository.insert(restoredReceipt)
                    receiptsRestored++
'''
new = '''                    val upsertResolution = localRepository.upsertRestoredReceipt(restoredReceipt)
                    Log.d(
                        TAG,
                        "Restore upsert ${upsertResolution.matchedBy}: internalId=${upsertResolution.receipt.internalId}, roomId=${upsertResolution.receipt.id}"
                    )
                    receiptsRestored++
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"Expected exactly one full-restore insert call, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")

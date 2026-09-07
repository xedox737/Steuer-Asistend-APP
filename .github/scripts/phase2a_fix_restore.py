from pathlib import Path

p = Path('app/src/main/java/com/example/data/SupplementalDriveBackup.kt')
s = p.read_text()
misplaced = '''            val learningRules = root.optJSONArray("bankLearningRules") ?: JSONArray()
            for (index in 0 until learningRules.length()) database.bankLearningRuleDao().upsertRule(learningRules.getJSONObject(index).toBankLearningRule())
            val learningEvidence = root.optJSONArray("bankRuleEvidence") ?: JSONArray()
            for (index in 0 until learningEvidence.length()) database.bankLearningRuleDao().insertEvidence(learningEvidence.getJSONObject(index).toBankRuleEvidence())
            database.managedDocumentDao().clearSearchIndex()
'''
if misplaced not in s:
    raise SystemExit('misplaced learning restore block not found')
s = s.replace(misplaced, '            database.managedDocumentDao().clearSearchIndex()\n', 1)
anchor = '''            val bankLinks = root.optJSONArray("bankReceiptLinks") ?: JSONArray()
            for (index in 0 until bankLinks.length()) {
                val restored = bankLinks.getJSONObject(index).toBankReceiptLink()
                val resolvedReceiptId = restored.receiptInternalId.takeIf { it.isNotBlank() }
                    ?.let { database.receiptDao().getReceiptByInternalId(it)?.id }
                    ?: restored.receiptId
                database.bankDao().upsertLink(restored.copy(receiptId = resolvedReceiptId))
            }
            database.managedDocumentDao().clearSearchIndex()
'''
replacement = '''            val bankLinks = root.optJSONArray("bankReceiptLinks") ?: JSONArray()
            for (index in 0 until bankLinks.length()) {
                val restored = bankLinks.getJSONObject(index).toBankReceiptLink()
                val resolvedReceiptId = restored.receiptInternalId.takeIf { it.isNotBlank() }
                    ?.let { database.receiptDao().getReceiptByInternalId(it)?.id }
                    ?: restored.receiptId
                database.bankDao().upsertLink(restored.copy(receiptId = resolvedReceiptId))
            }
            // Phase 2A learning data is restored on every normal backup restore. It must not
            // depend on the optional destructive replacement of managed documents above.
            val learningRules = root.optJSONArray("bankLearningRules") ?: JSONArray()
            for (index in 0 until learningRules.length()) database.bankLearningRuleDao().upsertRule(learningRules.getJSONObject(index).toBankLearningRule())
            val learningEvidence = root.optJSONArray("bankRuleEvidence") ?: JSONArray()
            for (index in 0 until learningEvidence.length()) database.bankLearningRuleDao().insertEvidence(learningEvidence.getJSONObject(index).toBankRuleEvidence())
            database.managedDocumentDao().clearSearchIndex()
'''
if anchor not in s:
    raise SystemExit('normal restore anchor not found')
p.write_text(s.replace(anchor, replacement, 1))
print('phase2a learning restore placement fixed')

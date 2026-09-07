package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.abs

object BankRuleType { const val COUNTERPARTY="COUNTERPARTY"; const val PURPOSE="PURPOSE"; const val AMOUNT="AMOUNT"; const val COMBINED="COMBINED" }
object BankRuleDirection { const val INCOME="INCOME"; const val EXPENSE="EXPENSE"; const val ANY="ANY" }
object BankRuleSource { const val LEARNED_FROM_CONFIRMATIONS="LEARNED_FROM_CONFIRMATIONS"; const val USER_CREATED="USER_CREATED"; const val USER_EDITED="USER_EDITED"; const val IMPORTED="IMPORTED" }
object BankRuleState { const val PROPOSED="PROPOSED"; const val ACTIVE="ACTIVE"; const val REJECTED="REJECTED" }

@Entity(tableName="bank_learning_rules", indices=[Index(value=["enabled"]), Index(value=["accountId"]), Index(value=["propertyId"]), Index(value=["unitId"])])
data class BankLearningRule(
    @PrimaryKey val ruleId:String,
    val displayName:String,
    val enabled:Boolean=false,
    val state:String=BankRuleState.PROPOSED,
    val ruleType:String=BankRuleType.COMBINED,
    val transactionDirection:String=BankRuleDirection.ANY,
    val counterpartyPattern:String="",
    val counterpartyIbanPattern:String="",
    val purposeTerms:String="",
    val amountMin:Double?=null,
    val amountMax:Double?=null,
    val currency:String="EUR",
    val accountId:String="",
    val propertyId:String="",
    val unitId:String="",
    val receiptVendorTarget:String="",
    val receiptCategoryTarget:String="",
    val receiptSubcategoryTarget:String="",
    val paymentMethodTarget:String="",
    val evidenceCount:Int=0,
    val successCount:Int=0,
    val rejectionCount:Int=0,
    val confidence:Int=0,
    val source:String=BankRuleSource.LEARNED_FROM_CONFIRMATIONS,
    val createdAt:String="",
    val updatedAt:String="",
    val lastMatchedAt:String=""
)

@Entity(tableName="bank_rule_evidence", indices=[Index(value=["candidateKey"]), Index(value=["transactionId"]), Index(value=["ruleId"])])
data class BankRuleEvidence(
    @PrimaryKey val evidenceId:String,
    val candidateKey:String,
    val ruleId:String="",
    val transactionId:String,
    val receiptId:Int,
    val confirmed:Boolean=true,
    val counterparty:String="",
    val direction:String,
    val purposeTerms:String="",
    val accountId:String="",
    val propertyId:String="",
    val unitId:String="",
    val vendorTarget:String="",
    val categoryTarget:String="",
    val subcategoryTarget:String="",
    val paymentMethodTarget:String="",
    val createdAt:String=""
)

@Dao
interface BankLearningRuleDao {
    @Query("SELECT * FROM bank_learning_rules ORDER BY enabled DESC, confidence DESC, updatedAt DESC") fun observeRules():Flow<List<BankLearningRule>>
    @Query("SELECT * FROM bank_learning_rules ORDER BY enabled DESC, confidence DESC, updatedAt DESC") suspend fun getAllRules():List<BankLearningRule>
    @Query("SELECT * FROM bank_learning_rules WHERE enabled = 1 AND state = 'ACTIVE'") suspend fun getActiveRules():List<BankLearningRule>
    @Query("SELECT * FROM bank_learning_rules WHERE ruleId=:ruleId LIMIT 1") suspend fun getRule(ruleId:String):BankLearningRule?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertRule(rule:BankLearningRule)
    @Query("DELETE FROM bank_learning_rules WHERE ruleId=:ruleId") suspend fun deleteRule(ruleId:String)
    @Query("SELECT * FROM bank_rule_evidence ORDER BY createdAt") suspend fun getAllEvidence():List<BankRuleEvidence>
    @Query("SELECT * FROM bank_rule_evidence WHERE candidateKey=:candidateKey ORDER BY createdAt") suspend fun getEvidence(candidateKey:String):List<BankRuleEvidence>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertEvidence(evidence:BankRuleEvidence):Long
    @Query("DELETE FROM bank_rule_evidence WHERE evidenceId=:evidenceId") suspend fun deleteEvidence(evidenceId:String)
}

data class BankRuleSuggestion(val ruleId:String,val score:Int,val confidence:Int,val reasons:List<String>,val suggestedPropertyId:String="",val suggestedUnitId:String="",val suggestedVendor:String="",val suggestedCategory:String="",val suggestedSubcategory:String="",val suggestedPaymentMethod:String="")
data class BankRuleEvaluation(val suggestions:List<BankRuleSuggestion>, val hasConflict:Boolean, val conflictReason:String="")

object BankLearningThresholds { const val MIN_EVIDENCE_FOR_RULE_SUGGESTION=3; const val MAX_RULE_BONUS=15; const val MIN_BASE_SCORE_FOR_RULE_BONUS=45 }

object BankRuleConfidence {
    fun calculate(evidence:Int, success:Int, rejection:Int, specificity:Int):Int {
        if (evidence<=0) return 0
        val attempts=(success+rejection).coerceAtLeast(1)
        val successRate=(success*55/attempts)
        val evidenceWeight=(evidence.coerceAtMost(10)*3)
        return (successRate+evidenceWeight+specificity.coerceIn(0,15)).coerceIn(0,100)
    }
}

object BankRuleEngine {
    fun evaluate(transaction:BankTransaction, rules:List<BankLearningRule>):BankRuleEvaluation {
        val matches=rules.asSequence().filter { it.enabled && it.state==BankRuleState.ACTIVE }.mapNotNull { score(transaction,it) }.sortedWith(compareByDescending<BankRuleSuggestion>{it.score}.thenByDescending{it.confidence}.thenBy{it.ruleId}).toList()
        if(matches.size<2) return BankRuleEvaluation(matches,false)
        val a=rules.first{it.ruleId==matches[0].ruleId}; val b=rules.first{it.ruleId==matches[1].ruleId}
        val conflict=matches[0].score==matches[1].score && targetsConflict(a,b)
        return BankRuleEvaluation(matches,conflict,if(conflict) "Mehrere Regeln passen" else "")
    }
    fun score(tx:BankTransaction, rule:BankLearningRule):BankRuleSuggestion? {
        if(!rule.enabled || rule.state!=BankRuleState.ACTIVE) return null
        if(rule.currency.isNotBlank() && !rule.currency.equals(tx.currency,true)) return null
        if(rule.accountId.isNotBlank() && rule.accountId!=tx.accountId) return null
        if(rule.propertyId.isNotBlank() && rule.propertyId!=tx.propertyId) return null
        if(rule.unitId.isNotBlank() && rule.unitId!=tx.unitId) return null
        val direction=if(tx.amount>=0) BankRuleDirection.INCOME else BankRuleDirection.EXPENSE
        if(rule.transactionDirection!=BankRuleDirection.ANY && rule.transactionDirection!=direction) return null
        val amount=abs(tx.amount)
        if(rule.amountMin!=null && amount<rule.amountMin) return null
        if(rule.amountMax!=null && amount>rule.amountMax) return null
        val cp=norm(tx.counterparty); val cpPattern=norm(rule.counterpartyPattern)
        if(cpPattern.isNotBlank() && cpPattern !in cp && cp !in cpPattern) return null
        val iban=norm(tx.counterpartyIban); val ibanPattern=norm(rule.counterpartyIbanPattern)
        if(ibanPattern.isNotBlank() && ibanPattern !in iban) return null
        val terms=rule.purposeTerms.split('|',',',';').map(::norm).filter{it.isNotBlank()}
        val purpose=norm(tx.purpose)
        if(terms.isNotEmpty() && terms.none{it in purpose}) return null
        var score=35; val reasons=mutableListOf<String>()
        if(cpPattern.isNotBlank()){score+=25; reasons += "Gegenpartei passt"}
        if(terms.isNotEmpty()){score+=15; reasons += "Verwendungszweck passt"}
        if(rule.amountMin!=null || rule.amountMax!=null){score+=10; reasons += "Betrag passt"}
        if(rule.accountId.isNotBlank()){score+=5; reasons += "Konto passt"}
        if(rule.propertyId.isNotBlank()){score+=5; reasons += "Objekt passt"}
        if(rule.unitId.isNotBlank()){score+=5; reasons += "Einheit passt"}
        reasons += "Regel „${rule.displayName}“"
        return BankRuleSuggestion(rule.ruleId,score.coerceIn(0,100),rule.confidence,reasons,rule.propertyId,rule.unitId,rule.receiptVendorTarget,rule.receiptCategoryTarget,rule.receiptSubcategoryTarget,rule.paymentMethodTarget)
    }
    private fun targetsConflict(a:BankLearningRule,b:BankLearningRule)=listOf(a.propertyId to b.propertyId,a.unitId to b.unitId,a.receiptCategoryTarget to b.receiptCategoryTarget,a.receiptVendorTarget to b.receiptVendorTarget).any{(x,y)->x.isNotBlank()&&y.isNotBlank()&&x!=y}
    internal fun norm(v:String)=v.lowercase(java.util.Locale.GERMANY).replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("ß","ss").replace(Regex("[^a-z0-9]+")," ").trim()
}

object BankRuleScoring {
    fun enhance(base:BankMatchSuggestion, transaction:BankTransaction, receipt:Receipt, rules:List<BankLearningRule>):BankMatchSuggestion {
        if(base.score<BankLearningThresholds.MIN_BASE_SCORE_FOR_RULE_BONUS) return base
        val evaluation=BankRuleEngine.evaluate(transaction,rules)
        if(evaluation.hasConflict) return base.copy(reasons=(base.reasons+evaluation.conflictReason).distinct())
        val best=evaluation.suggestions.firstOrNull() ?: return base
        val rule=rules.firstOrNull{it.ruleId==best.ruleId} ?: return base
        val targetFits=(rule.receiptVendorTarget.isBlank() || BankRuleEngine.norm(rule.receiptVendorTarget) in BankRuleEngine.norm(receipt.aussteller)) &&
            (rule.receiptCategoryTarget.isBlank() || rule.receiptCategoryTarget.equals(receipt.hauptkategorie,true)) &&
            (rule.receiptSubcategoryTarget.isBlank() || rule.receiptSubcategoryTarget.equals(receipt.unterkategorie,true))
        if(!targetFits) return base
        val bonus=(best.score/7).coerceIn(1,BankLearningThresholds.MAX_RULE_BONUS)
        return base.copy(score=(base.score+bonus).coerceIn(0,100), confidence=when{base.score+bonus>=85->"HOCH";base.score+bonus>=65->"MITTEL";else->base.confidence}, reasons=(base.reasons+"Regel „${rule.displayName}“ +$bonus").distinct())
    }
}

class BankLearningService(private val dao:BankLearningRuleDao, private val now:()->String={Instant.now().toString()}) {
    suspend fun recordConfirmed(transaction:BankTransaction, receipt:Receipt):BankLearningRule? {
        val direction=if(transaction.amount>=0) BankRuleDirection.INCOME else BankRuleDirection.EXPENSE
        val cp=BankRuleEngine.norm(transaction.counterparty)
        val property=receipt.propertyId
        val unit=receipt.wohneinheit.trim()
        val key=hash(listOf(cp,direction,property,unit,BankRuleEngine.norm(receipt.aussteller),BankRuleEngine.norm(receipt.hauptkategorie)).joinToString("|"))
        val evidenceId="evidence-"+hash("${transaction.transactionId}|${receipt.id}|$key").take(24)
        dao.insertEvidence(BankRuleEvidence(evidenceId,key,transactionId=transaction.transactionId,receiptId=receipt.id,counterparty=transaction.counterparty,direction=direction,purposeTerms=purposeTerms(transaction.purpose),accountId=transaction.accountId,propertyId=property,unitId=unit,vendorTarget=receipt.aussteller,categoryTarget=receipt.hauptkategorie,subcategoryTarget=receipt.unterkategorie,paymentMethodTarget=receipt.zahlungsart,createdAt=now()))
        val evidence=dao.getEvidence(key)
        if(evidence.size<BankLearningThresholds.MIN_EVIDENCE_FOR_RULE_SUGGESTION) return null
        val ruleId="rule-"+hash(key).take(24)
        val existing=dao.getRule(ruleId)
        if(existing?.state==BankRuleState.REJECTED || existing?.state==BankRuleState.ACTIVE) return existing
        val first=evidence.first(); val created=existing?.createdAt?.ifBlank{now()}?:now()
        val confidence=BankRuleConfidence.calculate(evidence.size,0,0,specificity(first))
        val rule=BankLearningRule(ruleId,displayName=displayName(first),enabled=false,state=BankRuleState.PROPOSED,ruleType=BankRuleType.COMBINED,transactionDirection=first.direction,counterpartyPattern=first.counterparty,purposeTerms=commonPurpose(evidence),currency=transaction.currency,accountId="",propertyId=first.propertyId,unitId=first.unitId,receiptVendorTarget=first.vendorTarget,receiptCategoryTarget=first.categoryTarget,receiptSubcategoryTarget=first.subcategoryTarget,paymentMethodTarget=first.paymentMethodTarget,evidenceCount=evidence.size,confidence=confidence,source=BankRuleSource.LEARNED_FROM_CONFIRMATIONS,createdAt=created,updatedAt=now())
        dao.upsertRule(rule); return rule
    }
    suspend fun accept(ruleId:String){ val r=dao.getRule(ruleId)?:return; dao.upsertRule(r.copy(enabled=true,state=BankRuleState.ACTIVE,successCount=r.successCount+1,confidence=BankRuleConfidence.calculate(r.evidenceCount,r.successCount+1,r.rejectionCount,specificity(r)),updatedAt=now(),lastMatchedAt=now())) }
    suspend fun reject(ruleId:String){ val r=dao.getRule(ruleId)?:return; dao.upsertRule(r.copy(enabled=false,state=BankRuleState.REJECTED,rejectionCount=r.rejectionCount+1,confidence=BankRuleConfidence.calculate(r.evidenceCount,r.successCount,r.rejectionCount+1,specificity(r)),updatedAt=now())) }
    suspend fun recordRuleSuccess(ruleId:String){ val r=dao.getRule(ruleId)?:return; dao.upsertRule(r.copy(successCount=r.successCount+1,evidenceCount=r.evidenceCount+1,confidence=BankRuleConfidence.calculate(r.evidenceCount+1,r.successCount+1,r.rejectionCount,specificity(r)),lastMatchedAt=now(),updatedAt=now())) }
    suspend fun recordRuleRejection(ruleId:String){ val r=dao.getRule(ruleId)?:return; dao.upsertRule(r.copy(rejectionCount=r.rejectionCount+1,confidence=BankRuleConfidence.calculate(r.evidenceCount,r.successCount,r.rejectionCount+1,specificity(r)),updatedAt=now())) }
    private fun specificity(e:BankRuleEvidence)=listOf(e.counterparty,e.purposeTerms,e.accountId,e.propertyId,e.unitId).count{it.isNotBlank()}*3
    private fun specificity(r:BankLearningRule)=listOf(r.counterpartyPattern,r.purposeTerms,r.accountId,r.propertyId,r.unitId).count{it.isNotBlank()}*3
    private fun displayName(e:BankRuleEvidence)="${e.counterparty.ifBlank{"Bankregel"}} → ${e.categoryTarget.ifBlank{e.vendorTarget}}"
    private fun purposeTerms(v:String)=BankRuleEngine.norm(v).split(' ').filter{it.length>=4}.take(4).joinToString("|")
    private fun commonPurpose(items:List<BankRuleEvidence>):String { val sets=items.map{it.purposeTerms.split('|').filter(String::isNotBlank).toSet()}; return if(sets.isEmpty()) "" else sets.reduce{a,b->a intersect b}.take(4).joinToString("|") }
    private fun hash(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
}

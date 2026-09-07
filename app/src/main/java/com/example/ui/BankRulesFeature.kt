package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BankLearningRule
import com.example.data.BankRuleDirection
import com.example.data.BankRuleSource
import com.example.data.BankRuleState
import com.example.data.BankRuleType
import java.time.Instant

@Composable
fun BankRulesPanel(viewModel: ReceiptViewModel, rules: List<BankLearningRule>) {
    var editing by remember { mutableStateOf<BankLearningRule?>(null) }
    var creating by remember { mutableStateOf(false) }
    Card(colors=CardDefaults.cardColors(containerColor=androidx.compose.material3.MaterialTheme.colorScheme.surface), border=BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Column { Text("Bankregeln", fontWeight=FontWeight.Bold, color=DarkNavy); Text("Transparentes Lern- und Vorschlagswissen – keine automatische Buchung.", color=SlateGray) }
                Button(onClick={ creating=true }) { Text("Neue Bankregel") }
            }
            val proposed=rules.filter{it.state==BankRuleState.PROPOSED}
            if(proposed.isNotEmpty()) {
                Text("Neue Lernvorschläge", fontWeight=FontWeight.SemiBold, color=DarkNavy)
                proposed.forEach { rule -> RuleCard(rule, onEdit={editing=rule}, onToggle={viewModel.acceptBankRule(rule.ruleId)}, onDelete={viewModel.rejectBankRule(rule.ruleId)}, proposal=true) }
            }
            val active=rules.filter{it.state==BankRuleState.ACTIVE && it.enabled}
            val inactive=rules.filter{it.state==BankRuleState.ACTIVE && !it.enabled}
            if(active.isNotEmpty()) Text("Aktive Regeln", fontWeight=FontWeight.SemiBold, color=DarkNavy)
            active.forEach { rule -> RuleCard(rule,{editing=rule},{viewModel.setBankRuleEnabled(rule.ruleId,false)},{viewModel.deleteBankRule(rule.ruleId)}) }
            if(inactive.isNotEmpty()) Text("Deaktivierte Regeln", fontWeight=FontWeight.SemiBold, color=DarkNavy)
            inactive.forEach { rule -> RuleCard(rule,{editing=rule},{viewModel.setBankRuleEnabled(rule.ruleId,true)},{viewModel.deleteBankRule(rule.ruleId)}) }
            if(rules.isEmpty()) Text("Noch keine Bankregeln. Nach mehreren bestätigten ähnlichen Zuordnungen kann die App einen Regelvorschlag anbieten.", color=SlateGray)
        }
    }
    if(creating) RuleEditorDialog(null,onDismiss={creating=false}) { viewModel.saveBankRule(it); creating=false }
    editing?.let { current -> RuleEditorDialog(current,onDismiss={editing=null}) { viewModel.saveBankRule(it.copy(source=BankRuleSource.USER_EDITED)); editing=null } }
}

@Composable
private fun RuleCard(rule:BankLearningRule,onEdit:()->Unit,onToggle:()->Unit,onDelete:()->Unit,proposal:Boolean=false) {
    Card(Modifier.fillMaxWidth(), border=BorderStroke(1.dp, BorderColor)) { Column(Modifier.padding(10.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(rule.displayName, fontWeight=FontWeight.SemiBold, color=DarkNavy)
        Text("Wenn: ${conditionSummary(rule)}", color=SlateGray)
        Text("Dann bevorzugen: ${targetSummary(rule)}", color=SlateGray)
        Text("Confidence ${rule.confidence}% • Evidenz ${rule.evidenceCount} • Quelle ${sourceLabel(rule.source)}" + rule.lastMatchedAt.takeIf{it.isNotBlank()}?.let{" • letzter Treffer $it"}.orEmpty(), color=SlateGray)
        if(rule.rejectionCount>0) Text("${rule.rejectionCount} Ablehnung(en) – Regel prüfen", color=SlateGray)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            TextButton(onClick=onEdit){Text(if(proposal) "Vor Aktivierung bearbeiten" else "Bearbeiten")}
            TextButton(onClick=onToggle){Text(if(proposal) "Regel übernehmen" else if(rule.enabled) "Deaktivieren" else "Aktivieren")}
            TextButton(onClick=onDelete){Text(if(proposal) "Ablehnen" else "Löschen")}
        }
    }}
}

private fun conditionSummary(r:BankLearningRule)=listOfNotNull(r.counterpartyPattern.takeIf{it.isNotBlank()}?.let{"Gegenpartei enthält „$it“"},r.counterpartyIbanPattern.takeIf{it.isNotBlank()}?.let{"IBAN enthält „$it“"},r.purposeTerms.takeIf{it.isNotBlank()}?.let{"Zweck: ${it.replace('|',',')}"},r.amountMin?.let{"ab %.2f €".format(it)},r.amountMax?.let{"bis %.2f €".format(it)},r.accountId.takeIf{it.isNotBlank()}?.let{"Konto $it"},r.propertyId.takeIf{it.isNotBlank()}?.let{"Objekt $it"},r.unitId.takeIf{it.isNotBlank()}?.let{"Einheit $it"},r.transactionDirection.takeIf{it!=BankRuleDirection.ANY}).joinToString(" • ").ifBlank{"allgemeine Bankbuchung"}
private fun targetSummary(r:BankLearningRule)=listOfNotNull(r.receiptVendorTarget.takeIf{it.isNotBlank()}?.let{"Händler $it"},r.receiptCategoryTarget.takeIf{it.isNotBlank()}?.let{"Kategorie $it"},r.receiptSubcategoryTarget.takeIf{it.isNotBlank()}?.let{"Unterkategorie $it"},r.paymentMethodTarget.takeIf{it.isNotBlank()}?.let{"Zahlungsart $it"}).joinToString(" • ").ifBlank{"passende Belege"}
private fun sourceLabel(v:String)=when(v){BankRuleSource.LEARNED_FROM_CONFIRMATIONS->"Aus Bestätigungen gelernt";BankRuleSource.USER_CREATED->"Vom Nutzer erstellt";BankRuleSource.USER_EDITED->"Vom Nutzer bearbeitet";BankRuleSource.IMPORTED->"Importiert";else->v}

@Composable
private fun RuleEditorDialog(existing:BankLearningRule?,onDismiss:()->Unit,onSave:(BankLearningRule)->Unit) {
    var name by remember(existing){mutableStateOf(existing?.displayName.orEmpty())}; var cp by remember(existing){mutableStateOf(existing?.counterpartyPattern.orEmpty())}; var iban by remember(existing){mutableStateOf(existing?.counterpartyIbanPattern.orEmpty())}; var purpose by remember(existing){mutableStateOf(existing?.purposeTerms.orEmpty())}; var min by remember(existing){mutableStateOf(existing?.amountMin?.toString().orEmpty())}; var max by remember(existing){mutableStateOf(existing?.amountMax?.toString().orEmpty())}; var account by remember(existing){mutableStateOf(existing?.accountId.orEmpty())}; var property by remember(existing){mutableStateOf(existing?.propertyId.orEmpty())}; var unit by remember(existing){mutableStateOf(existing?.unitId.orEmpty())}; var vendor by remember(existing){mutableStateOf(existing?.receiptVendorTarget.orEmpty())}; var category by remember(existing){mutableStateOf(existing?.receiptCategoryTarget.orEmpty())}; var subcategory by remember(existing){mutableStateOf(existing?.receiptSubcategoryTarget.orEmpty())}; var payment by remember(existing){mutableStateOf(existing?.paymentMethodTarget.orEmpty())}; var direction by remember(existing){mutableStateOf(existing?.transactionDirection?:BankRuleDirection.ANY)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"Neue Bankregel" else "Bankregel bearbeiten")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        OutlinedTextField(name,{name=it},label={Text("Regelname")}); OutlinedTextField(cp,{cp=it},label={Text("Gegenpartei enthält")}); OutlinedTextField(iban,{iban=it},label={Text("Gegenpartei-IBAN enthält (optional)")}); OutlinedTextField(purpose,{purpose=it},label={Text("Verwendungszweck enthält")}); Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){ listOf(BankRuleDirection.ANY to "Alle",BankRuleDirection.EXPENSE to "Ausgabe",BankRuleDirection.INCOME to "Einnahme").forEach{(v,l)->FilterChip(direction==v,{direction=v},{Text(l)})} }; OutlinedTextField(min,{min=it},label={Text("Betrag von")}); OutlinedTextField(max,{max=it},label={Text("Betrag bis")}); Text("Gültigkeitsbereich (leer = alle)", fontWeight=FontWeight.SemiBold, color=DarkNavy); OutlinedTextField(account,{account=it},label={Text("Konto-ID")}); OutlinedTextField(property,{property=it},label={Text("Objekt-ID")}); OutlinedTextField(unit,{unit=it},label={Text("Einheit")}); Text("Vorschlagsziel", fontWeight=FontWeight.SemiBold, color=DarkNavy); OutlinedTextField(vendor,{vendor=it},label={Text("Händler bevorzugen")}); OutlinedTextField(category,{category=it},label={Text("Kategorie bevorzugen")}); OutlinedTextField(subcategory,{subcategory=it},label={Text("Unterkategorie bevorzugen")}); OutlinedTextField(payment,{payment=it},label={Text("Zahlungsart vorschlagen")}); Text("Zusammenfassung: Wenn ${cp.ifBlank{"Buchung"}} passt, werden passende Belege nur höher priorisiert; es erfolgt keine automatische Buchung oder steuerliche Zuordnung.", color=SlateGray)
    }},confirmButton={Button(onClick={val now=Instant.now().toString(); val id=existing?.ruleId?:"user-rule-${java.util.UUID.randomUUID()}"; onSave((existing?:BankLearningRule(id,name.ifBlank{"Bankregel"})).copy(displayName=name.ifBlank{"Bankregel"},enabled=existing?.enabled?:true,state=BankRuleState.ACTIVE,ruleType=BankRuleType.COMBINED,transactionDirection=direction,counterpartyPattern=cp,counterpartyIbanPattern=iban,purposeTerms=purpose.replace(',', '|'),amountMin=min.replace(',','.').toDoubleOrNull(),amountMax=max.replace(',','.').toDoubleOrNull(),accountId=account,propertyId=property,unitId=unit,receiptVendorTarget=vendor,receiptCategoryTarget=category,receiptSubcategoryTarget=subcategory,paymentMethodTarget=payment,source=existing?.source?:BankRuleSource.USER_CREATED,createdAt=existing?.createdAt?.ifBlank{now}?:now,updatedAt=now))}){Text("Speichern")}},dismissButton={OutlinedButton(onClick=onDismiss){Text("Abbrechen")}})
}

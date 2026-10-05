package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig

private data class HelpQuestion(val question: String, val answer: String)
private data class HelpSection(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val questions: List<HelpQuestion>)

private val helpSections = listOf(
    HelpSection("Erste Schritte", Icons.Default.RocketLaunch, AccentBlue, listOf(
        HelpQuestion("Wie starte ich mit ImmoPilot?", "Lege zuerst deine Immobilie und die zugehörigen Wohneinheiten an. Danach kannst du Belege erfassen, Dokumente zuordnen, Mieteingänge prüfen und weitere Bereiche nutzen."),
        HelpQuestion("Muss ich Google Drive verwenden?", "Nein. ImmoPilot ist lokal-first und bleibt ohne Cloud-Verbindung nutzbar. Google Drive ist eine optionale Sicherungsfunktion.")
    )),
    HelpSection("Immobilien & Einheiten", Icons.Default.Apartment, AccentBlue, listOf(
        HelpQuestion("Wo verwalte ich Wohnungen und Mietverhältnisse?", "Öffne unten den Bereich Immobilien. Dort findest du deine Objekte, Wohneinheiten, Mietverhältnisse, Dokumente und weitere objektbezogene Funktionen."),
        HelpQuestion("Wie ändere ich Mietdaten?", "Öffne die betreffende Immobilie und Wohneinheit. Änderungen an Miete und Mietverhältnis erfolgen dort, damit die Zuordnung zur Einheit erhalten bleibt.")
    )),
    HelpSection("Belege & Dokumente", Icons.Default.ReceiptLong, WarmOrange, listOf(
        HelpQuestion("Wie ordne ich einen Beleg einer Immobilie zu?", "Öffne den Beleg und wähle dort die passende Immobilien- bzw. Einheitenzuordnung. Änderungen solltest du anschließend speichern."),
        HelpQuestion("Wo finde ich meine Dokumente?", "Die Dokumentenakte ist unter Mehr → Verwaltung erreichbar. Objektbezogene Dokumente findest du zusätzlich im jeweiligen Immobilienbereich.")
    )),
    HelpSection("Bank & Buchungen", Icons.Default.AccountBalance, AccentBlue, listOf(
        HelpQuestion("Wo finde ich Kontoauszüge und Bankbuchungen?", "Öffne Mehr → Finanzen → Bank & Kontoauszüge. Dort kannst du importierte Buchungen prüfen und mit Belegen verknüpfen."),
        HelpQuestion("Warum ist eine Buchung noch nicht zugeordnet?", "Eine Buchung bleibt offen, solange noch keine eindeutige Beleg-, Kategorie- oder Immobilienzuordnung bestätigt wurde. Öffne die Buchungsdetails und prüfe die Zuordnung.")
    )),
    HelpSection("Mieteingänge", Icons.Default.HomeWork, EmeraldGreen, listOf(
        HelpQuestion("Was bedeutet Soll und Ist?", "Soll zeigt die erwarteten Mietzahlungen aus den hinterlegten Mietdaten. Ist zeigt die tatsächlich erfassten bzw. zugeordneten Mieteingänge."),
        HelpQuestion("Warum wird ein offener Betrag angezeigt?", "Ein offener Betrag entsteht, wenn das berechnete Soll höher ist als die zugeordneten Ist-Einnahmen im betrachteten Zeitraum.")
    )),
    HelpSection("Einnahmen & Ausgaben", Icons.Default.Payments, CrimsonRed, listOf(
        HelpQuestion("Was zeigt die Finanzübersicht?", "Die Übersicht fasst Einnahmen, Ausgaben, Ergebnis, Verlauf und Buchungen zusammen. Filter helfen dir, nach Jahr, Immobilie, Kategorie und Zeitraum einzugrenzen.")
    )),
    HelpSection("DATEV & Steuern", Icons.Default.Assessment, WarmOrange, listOf(
        HelpQuestion("Wo finde ich den DATEV-Export?", "Öffne Mehr → Steuern & Auswertung → DATEV Export."),
        HelpQuestion("Werden KI-Ergebnisse automatisch exportiert?", "Nein. KI-Vorschläge müssen geprüft werden; ungeprüfte KI-Daten sollen nicht automatisch in DATEV übernommen werden.")
    )),
    HelpSection("Backup & Wiederherstellung", Icons.Default.Cloud, EmeraldGreen, listOf(
        HelpQuestion("Wo finde ich Backup & Cloud?", "Öffne Mehr → Daten & Sicherung → Backup & Cloud."),
        HelpQuestion("Bleiben meine lokalen Daten ohne Cloud erhalten?", "Ja. Die App ist lokal-first. Eine Cloud-Verbindung ist keine Voraussetzung für die Nutzung.")
    )),
    HelpSection("KI & API-Schlüssel", Icons.Default.AutoAwesome, Color(0xFF7C3AED), listOf(
        HelpQuestion("Wo richte ich OpenAI, Gemini oder Google Routes ein?", "Öffne Mehr → Einstellungen → App-Einstellungen. Unter KI & Anbieter kannst du die vorhandenen Dienste konfigurieren."),
        HelpQuestion("Werden API-Schlüssel im Backup gespeichert?", "Nein. API-Schlüssel werden lokal geschützt gespeichert und nicht in die App-Sicherung oder Exporte aufgenommen.")
    )),
    HelpSection("Fahrtenbuch", Icons.Default.DirectionsCar, AccentBlue, listOf(
        HelpQuestion("Wo finde ich das Fahrtenbuch?", "Öffne Mehr → Verwaltung → Fahrtenbuch."),
        HelpQuestion("Wofür wird Google Routes verwendet?", "Wenn Google Routes eingerichtet ist, kann die vorhandene Fahrtenbuchfunktion Straßenentfernungen automatisch berechnen.")
    ))
)

@Composable
internal fun HelpFaqScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var expanded by remember { mutableStateOf(setOf<String>()) }

    HelpInfoPage(
        title = "Hilfe & FAQ",
        subtitle = "Antworten auf häufige Fragen zu ImmoPilot",
        onBack = onBack,
        tag = "help_faq"
    ) {
        helpSections.forEach { section ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Ui2.shape,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HelpIcon(section.icon, section.color)
                            Text(section.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        }
                        section.questions.forEachIndexed { index, question ->
                            if (index > 0) HorizontalDivider(color = BorderColor.copy(alpha = .65f))
                            val key = section.title + "|" + question.question
                            val isExpanded = key in expanded
                            Surface(
                                onClick = {
                                    expanded = if (isExpanded) expanded - key else expanded + key
                                },
                                modifier = Modifier.fillMaxWidth().testTag("faq_${section.title}_$index"),
                                color = Color.White,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(Modifier.padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(question.question, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                                        Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = SlateGray, modifier = Modifier.size(19.dp))
                                    }
                                    if (isExpanded) {
                                        Text(question.answer, fontSize = 11.sp, lineHeight = 16.sp, color = SlateGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AboutImmoPilotScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    HelpInfoPage(
        title = "Über ImmoPilot",
        subtitle = "App-Informationen und wichtige Hinweise",
        onBack = onBack,
        tag = "about"
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = Ui2.shape,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HelpIcon(Icons.Default.Home, AccentBlue)
                        Column {
                            Text("ImmoPilot App", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                            Text("Immobilien. Finanzen. Steuern.", fontSize = 11.sp, color = SlateGray)
                        }
                    }
                    HorizontalDivider(color = BorderColor.copy(alpha = .65f))
                    InfoLine("Version", BuildConfig.VERSION_NAME)
                    InfoLine("Datenhaltung", "Lokal-first · Cloud optional")
                }
            }
        }
        item {
            HelpNotice(
                title = "Wichtiger Hinweis",
                text = "ImmoPilot unterstützt bei Organisation und Auswertung. Steuerliche, rechtliche und finanzielle Angaben sowie KI-Vorschläge müssen vor der Verwendung geprüft werden.",
                icon = Icons.Default.Info,
                color = WarmOrange
            )
        }
        item {
            HelpNotice(
                title = "Datenschutz & Sicherheit",
                text = "Sensible Zugangsdaten wie API-Schlüssel werden nicht in App-Backups oder Exporte aufgenommen. Weitere Details findest du unter App-Einstellungen → Datenschutz.",
                icon = Icons.Default.Shield,
                color = AccentBlue
            )
        }
    }
}

@Composable
private fun HelpInfoPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    tag: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SoftBackground).testTag(tag),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).testTag("${tag}_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück zu Mehr", tint = DarkNavy)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = SlateGray)
                }
            }
        }
        content()
    }
}

@Composable
private fun HelpIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(Modifier.size(42.dp), shape = Ui2.iconShape, color = color.copy(alpha = .12f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = SlateGray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
    }
}

@Composable
private fun HelpNotice(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HelpIcon(icon, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = SlateGray)
            }
        }
    }
}

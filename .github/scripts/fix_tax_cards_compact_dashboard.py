from pathlib import Path

PATH = Path('app/src/main/java/com/example/ui/ReceiptAppUi.kt')
s = PATH.read_text(encoding='utf-8')

anchor = '''        Text(
            "Objekt, Drive, KI und weitere Einstellungen findest du oben rechts über das Zahnrad.",
            fontSize = 11.sp,
            color = SlateGray,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    return
'''

insert = '''        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AfA Gebäude", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${taxPhase1.afaRatePercent}% p.a.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                }
                Text("Gebäude-Kaufpreisanteil: ${NumberFormatter.format(taxPhase1.buildingPurchaseShare)}", fontSize = 11.sp, color = SlateGray)
                Text("+ anteilige Anschaffungsnebenkosten: ${NumberFormatter.format(taxPhase1.buildingAncillaryShare)}", fontSize = 11.sp, color = SlateGray)
                HorizontalDivider(color = BorderColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AfA-Bemessungsgrundlage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                    Text(NumberFormatter.format(taxPhase1.buildingAcquisitionCosts), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AfA volles Jahr", fontSize = 12.sp, color = SlateGray)
                    Text(NumberFormatter.format(taxPhase1.annualAfa), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
                if (taxPhase1.afaStartDate.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Erstes Jahr ab ${taxPhase1.afaStartDate}", fontSize = 11.sp, color = SlateGray)
                        Text(NumberFormatter.format(taxPhase1.firstYearAfa), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    }
                }
                Text(
                    "Vorbereitungswert. Kaufpreisaufteilung, Nebenkosten und AfA bitte vor der Steuererklärung prüfen.",
                    fontSize = 10.sp,
                    color = SlateGray,
                    lineHeight = 13.sp
                )
            }
        }

        val compactProgress15 = if (taxPhase1.limit15Percent > 0.0) {
            (taxPhase1.relevantModernizationNet / taxPhase1.limit15Percent).toFloat().coerceIn(0f, 1f)
        } else 0f
        val compact15Color = when {
            taxPhase1.is15PercentExceeded -> CrimsonRed
            compactProgress15 >= 0.8f -> WarmOrange
            else -> EmeraldGreen
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, if (taxPhase1.is15PercentExceeded) CrimsonRed else BorderColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("15%-Sanierungsmonitor", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${"%.1f".format(taxPhase1.limitUsagePercent)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = compact15Color)
                }
                LinearProgressIndicator(
                    progress = { compactProgress15 },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = compact15Color,
                    trackColor = Color(0xFFE2E8F0)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Prüfsumme netto", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(taxPhase1.relevantModernizationNet), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("15%-Prüfwert", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(taxPhase1.limit15Percent), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Text(
                    "Zeitraum: ${taxPhase1.monitorStartDate.ifBlank { "nicht festgelegt" }} bis ${taxPhase1.monitorEndDate.ifBlank { "nicht festgelegt" }} • ${taxPhase1.candidateReceiptCount} potenziell relevante Belege" +
                        if (taxPhase1.estimatedNetCount > 0) " • ${taxPhase1.estimatedNetCount} Nettobetrag/-beträge geschätzt" else "",
                    fontSize = 10.sp,
                    color = SlateGray,
                    lineHeight = 13.sp
                )
                if (taxPhase1.is15PercentExceeded) {
                    Text(
                        "Prüfwert überschritten: steuerliche Einordnung fachlich prüfen.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                }
            }
        }

        Text(
            "Objekt, Drive, KI und weitere Einstellungen findest du oben rechts über das Zahnrad.",
            fontSize = 11.sp,
            color = SlateGray,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    return
'''

if 'Text("AfA Gebäude", fontSize = 15.sp' in s:
    print('Compact tax cards already present')
elif anchor not in s:
    raise SystemExit('active dashboard anchor not found')
else:
    s = s.replace(anchor, insert, 1)
    PATH.write_text(s, encoding='utf-8')
    print('Compact dashboard AfA and 15% cards inserted')

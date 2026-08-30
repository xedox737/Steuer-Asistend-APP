from pathlib import Path

p = Path('app/src/main/java/com/example/ui/ReceiptAppUi.kt')
s = p.read_text(encoding='utf-8')

if 'title = "Mieteingänge", subtitle = "Soll/Ist & Nebenkosten"' in s:
    print('Mieteingänge-Link bereits vorhanden')
    raise SystemExit(0)

anchor = '''        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard(
                modifier = Modifier.weight(1f), title = "Fahrtenbuch", subtitle = "Fahrten erfassen",
                icon = Icons.Default.DirectionsCar, containerColor = Color(0xFFFFF7ED), contentColor = WarmOrange,
                onClick = { viewModel.setScreen(AppScreen.LOGBOOK) }
            )
            QuickActionCard(
                modifier = Modifier.weight(1f), title = "Steuerschätzung", subtitle = "Anlage V",
                icon = Icons.Filled.Calculate, containerColor = Color(0xFFF5F3FF), contentColor = Color(0xFF7C3AED),
                onClick = { viewModel.setScreen(AppScreen.TAX_CALCULATOR) }
            )
        }
'''

insert = anchor + '''        QuickActionCard(
            modifier = Modifier.fillMaxWidth().testTag("rent_overview_quick_action"),
            title = "Mieteingänge", subtitle = "Soll/Ist & Nebenkosten",
            icon = Icons.Default.Home, containerColor = Color(0xFFEFF6FF), contentColor = AccentBlue,
            onClick = { viewModel.setScreen(AppScreen.RENT_OVERVIEW) }
        )
'''

if anchor not in s:
    raise SystemExit('Schnellzugriff-Anker nicht gefunden')

p.write_text(s.replace(anchor, insert, 1), encoding='utf-8')
print('Mieteingänge-Link eingefügt')

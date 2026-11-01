package com.example.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Select the fixture's month through the real UI, independently of today's month/year. */
internal fun ComposeTestRule.selectMonthlyRentFixtureMonth(target: YearMonth) {
    val label = SemanticsMatcher("Monatsauswahl MM/JJJJ") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.matches(Regex("[0-9]{2}/[0-9]{4}")) } == true
    }
    val formatter = DateTimeFormatter.ofPattern("MM/uuuu")
    val shown = onNode(label and hasAnyAncestor(isDialog())).fetchSemanticsNode()
        .config[SemanticsProperties.Text].single().text
    val delta = ChronoUnit.MONTHS.between(YearMonth.parse(shown, formatter), target).toInt()
    repeat(abs(delta)) { onNodeWithText(if (delta < 0) "‹" else "›").performClick() }
    onNodeWithText(target.format(formatter)).assertExists()
}

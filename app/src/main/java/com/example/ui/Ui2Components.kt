package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared presentation tokens. These components do not own application state. */
object Ui2 {
    val spacing = 12.dp
    val padding = 16.dp
    val shape = RoundedCornerShape(16.dp)
}

@Composable
internal fun Ui2Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(Ui2.padding), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Collapse to a single column before large text or narrow screens can squeeze labels. */
@Composable
internal fun <T> Ui2Grid(
    items: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T, Modifier) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= (156 * fontScale * 2 + 12).dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                    row.forEach { item -> content(item, Modifier.weight(1f)) }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

internal data class Ui2Action(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color = AccentBlue,
    val onClick: () -> Unit
)

@Composable
internal fun Ui2ActionGrid(actions: List<Ui2Action>) {
    Ui2Grid(actions) { action, modifier ->
        Ui2ActionCard(action, modifier)
    }
}

@Composable
internal fun Ui2ActionCard(action: Ui2Action, modifier: Modifier = Modifier) {
    Card(
        onClick = action.onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(
            containerColor = action.color.copy(alpha = 0.09f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(action.icon, contentDescription = null, tint = action.color, modifier = Modifier.size(28.dp))
            Text(action.title, style = MaterialTheme.typography.titleSmall)
            if (action.subtitle.isNotBlank()) {
                Text(action.subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun Ui2Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = AccentBlue) {
    Surface(modifier, shape = Ui2.shape, color = color.copy(alpha = 0.08f)) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (label) {
                "Offene Buchungen" -> Icons.Default.AccountBalance
                "Offene Belege", "Belege gesamt" -> Icons.Default.Description
                "Regeln aktiv" -> Icons.Default.Rule
                else -> Icons.Default.ReceiptLong
            }
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(25.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun Ui2Destination(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color = AccentBlue,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = Ui2.shape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}


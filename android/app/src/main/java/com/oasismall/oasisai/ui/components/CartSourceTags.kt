package com.oasismall.oasisai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CartSourceTags {
    const val MANUAL = "SRC_MANUAL"
    const val STAMPER = "SRC_STAMPER"
    const val BATCH_TXT = "SRC_BATCH_TXT"
    const val CHECK_SHOOT = "SRC_CHECK_SHOOT"
    const val SCANNER = "SRC_SCANNER"
    const val HOME = "SRC_HOME"
    const val ARTICLE = "SRC_ARTICLE"
}

data class CartSourceStyle(
    val label: String,
    val color: Color,
)

fun cartSourceStyle(note: String?): CartSourceStyle = when (note) {
    CartSourceTags.STAMPER -> CartSourceStyle("Legacy Stamper", Color(0xFF2E7D32))
    CartSourceTags.BATCH_TXT -> CartSourceStyle("Batch txt", Color(0xFF1565C0))
    CartSourceTags.CHECK_SHOOT -> CartSourceStyle("AGENT", Color(0xFF6A1B9A))
    CartSourceTags.SCANNER -> CartSourceStyle("Scanner", Color(0xFF00838F))
    CartSourceTags.HOME -> CartSourceStyle("Articles search", Color(0xFFF57C00))
    CartSourceTags.ARTICLE -> CartSourceStyle("Article detail", Color(0xFFAD1457))
    else -> CartSourceStyle("Manual", Color(0xFF757575))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartSourceLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Origin colors", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    CartSourceTags.BATCH_TXT,
                    CartSourceTags.STAMPER,
                    CartSourceTags.CHECK_SHOOT,
                    CartSourceTags.SCANNER,
                    CartSourceTags.HOME,
                    CartSourceTags.ARTICLE,
                    CartSourceTags.MANUAL,
                ).forEach { key ->
                    val style = cartSourceStyle(key)
                    Text(
                        text = style.label,
                        color = style.color,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

fun sourceBorder(note: String?): BorderStroke? {
    val style = cartSourceStyle(note)
    return BorderStroke(2.dp, style.color)
}

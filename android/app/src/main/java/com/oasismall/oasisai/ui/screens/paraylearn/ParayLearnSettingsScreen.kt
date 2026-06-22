package com.oasismall.oasisai.ui.screens.paraylearn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.paray.ParayLearnSettings
import kotlin.math.roundToInt

private const val SLIDER_MIN = 0.01f
private const val SLIDER_MAX = 0.99f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParayLearnSettingsScreen(
    viewModel: ParayLearnSettingsViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PARAY Learn settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Tune how strictly PARAY Learn confirms the front PNG and auto-captures sides. " +
                        "Changes save immediately to learn_settings.json and apply to the next session.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                ThresholdCard(
                    title = "Front confirmation threshold",
                    description = "Minimum PNG-to-camera confidence required to validate front side.",
                    value = ui.settings.frontConfirmationThreshold,
                    onValueChange = viewModel::updateFront,
                    onReset = viewModel::resetFront,
                )
            }
            item {
                ThresholdCard(
                    title = "Side capture threshold",
                    description = "Minimum distinctiveness required before left/right auto-capture.",
                    value = ui.settings.sideCaptureThreshold,
                    onValueChange = viewModel::updateSide,
                    onReset = viewModel::resetSide,
                )
            }
            item {
                ThresholdCard(
                    title = "Back capture threshold",
                    description = "Minimum distinctiveness required before back auto-capture.",
                    value = ui.settings.backCaptureThreshold,
                    onValueChange = viewModel::updateBack,
                    onReset = viewModel::resetBack,
                )
            }
            item {
                OutlinedButton(
                    onClick = viewModel::resetAll,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset all to defaults")
                }
            }
            item {
                Text(
                    "Defaults: front ${formatThreshold(ParayLearnSettings.factoryDefaults().frontConfirmationThreshold)}, " +
                        "side ${formatThreshold(ParayLearnSettings.factoryDefaults().sideCaptureThreshold)}, " +
                        "back ${formatThreshold(ParayLearnSettings.factoryDefaults().backCaptureThreshold)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThresholdCard(
    title: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    formatThreshold(value),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = value.coerceIn(SLIDER_MIN, SLIDER_MAX),
                onValueChange = onValueChange,
                valueRange = SLIDER_MIN..SLIDER_MAX,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${(value * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedButton(onClick = onReset) {
                    Text("Reset to default")
                }
            }
        }
    }
}

private fun formatThreshold(value: Float): String =
    String.format("%.2f", value.coerceIn(SLIDER_MIN, SLIDER_MAX))

package com.oasismall.oasisai.ui.screens.print

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.util.ExportShareHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintScreen(viewModel: PrintViewModel) {
    val context = LocalContext.current
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val count by viewModel.preselectionCount.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Print Generator") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pre-selected articles: $count")
            Text("Workflow: Catalog → Pre-selection → Template → Export")
            RowSwitch("Promo print", uiState.isPromo) { viewModel.setPromo(it) }
            if (uiState.isPromo) {
                OutlinedTextField(
                    value = uiState.campaignName,
                    onValueChange = viewModel::setCampaignName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Campaign name") },
                )
                OutlinedTextField(
                    value = uiState.promoDays.toString(),
                    onValueChange = { it.toIntOrNull()?.let(viewModel::setPromoDays) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Promo duration (days)") },
                )
            }
            if (uiState.isGenerating) CircularProgressIndicator()
            uiState.lastResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (result.success) "Export ready" else "Export failed")
                        result.errorMessage?.let { Text(it) }
                        result.exportPath?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                OutlinedButton(
                                    onClick = { ExportShareHelper.openPdf(context, file) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Open PDF") }
                                OutlinedButton(
                                    onClick = { ExportShareHelper.sharePdf(context, file) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Share PDF") }
                            }
                        }
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates) { template ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(template.name, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text("${template.type} — ${template.size} — capacity ${template.capacity}")
                            Button(
                                onClick = { viewModel.generate(template) },
                                enabled = count > 0 && !uiState.isGenerating,
                            ) { Text("Generate PDF") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

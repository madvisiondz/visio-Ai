package com.oasismall.oasisai.ui.screens.batchtxt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchTxtScreen(
    viewModel: BatchTxtViewModel,
    onOpenCameraBatch: (queueItemId: Long?) -> Unit = {},
    onOpenPhotoroomImport: () -> Unit = {},
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val cameraQueue by viewModel.cameraQueue.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Batch txt") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Paste Sara designation list. Results: To share (has PNG), To shoot (no PNG), Camera batch (not in CSV).",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::setInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Batch text") },
                minLines = 8,
            )
            Button(onClick = viewModel::processBatch, modifier = Modifier.fillMaxWidth()) {
                Text("Process batch")
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            if (result.totalLines > 0) {
                Text("Total lines: ${result.totalLines}")
                Text("To share (PNG exists): ${result.matchedWithPng}")
                Text("To shoot (PNG missing): ${result.matchedMissingPng}")
                Text("Camera batch (not in CSV): ${result.notInCsv}")
            }
            if (cameraQueue.isNotEmpty()) {
                Text(
                    "Camera batch queue — tap to capture",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                cameraQueue.forEachIndexed { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCameraBatch(item.id) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${index + 1}. ${item.designation}", fontWeight = FontWeight.Medium)
                            Text("Tap → scan barcode, shoot, proceed", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(
                    onClick = { onOpenCameraBatch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start camera batch (${cameraQueue.size} in queue)")
                }
            }
            OutlinedButton(onClick = { onOpenCameraBatch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open camera batch (free scan)")
            }
            OutlinedButton(onClick = onOpenPhotoroomImport, modifier = Modifier.fillMaxWidth()) {
                Text("PhotoRoom import")
            }
        }
    }
}

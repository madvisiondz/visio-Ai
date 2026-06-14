package com.oasismall.oasisai.ui.screens.batchtxt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchTxtScreen(
    viewModel: BatchTxtViewModel,
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

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
                "Paste designation lines from Sara. Articles with PNG go to To share; missing PNG — use AGENT tab.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::setInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Batch text") },
                minLines = 10,
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
                Text("Need photo via AGENT (PNG missing): ${result.matchedMissingPng}")
                Text("Not found in catalog: ${result.unmatched}")
                if (result.unmatchedDesignations.isNotEmpty()) {
                    Text(
                        "First not found:\n${result.unmatchedDesignations.joinToString("\n")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

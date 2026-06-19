package com.oasismall.oasisai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ManualBarcodeEntrySection(
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Manual barcode",
    hint: String = "When the label won't scan, type the digits below.",
) {
    var barcode by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = barcode,
            onValueChange = { barcode = it.filter { ch -> ch.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Barcode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    val trimmed = barcode.trim()
                    if (trimmed.isNotEmpty()) {
                        onSubmit(trimmed)
                        barcode = ""
                    }
                },
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSubmit(barcode.trim())
                    barcode = ""
                },
                modifier = Modifier.weight(1f),
                enabled = barcode.isNotBlank(),
            ) { Text("Look up") }
            OutlinedButton(
                onClick = { barcode = "" },
                modifier = Modifier.weight(1f),
                enabled = barcode.isNotBlank(),
            ) { Text("Clear") }
        }
    }
}

@Composable
fun ManualBarcodeEntryDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    title: String = "Enter barcode",
    hint: String = "Type digits when the camera can't read the label.",
) {
    var barcode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Barcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = barcode.trim()
                            if (trimmed.isNotEmpty()) {
                                onSubmit(trimmed)
                                barcode = ""
                            }
                        },
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(barcode.trim())
                    barcode = ""
                },
                enabled = barcode.isNotBlank(),
            ) { Text("Look up") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

package com.oasismall.oasisai.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun AssignPngImageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    label: String = "Add PNG image",
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label)
    }
}

/** Returns a launcher callback — call it after setting which article receives the PNG. */
@Composable
fun rememberAssignPngPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onPicked(uri)
    }
    return remember(launcher) {
        {
            launcher.launch(arrayOf("image/png", "image/*", "application/octet-stream", "*/*"))
        }
    }
}

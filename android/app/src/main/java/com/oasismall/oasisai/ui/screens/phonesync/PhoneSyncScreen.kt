package com.oasismall.oasisai.ui.screens.phonesync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.phonesync.PhoneSyncProtocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSyncScreen(
    viewModel: PhoneSyncViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadSettings(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone sync (hotspot)") },
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
                    "Connect phones on one Wi‑Fi hotspot. One phone is master (receiver); others send new shelf PNGs after comparing catalogs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                RowChips(
                    role = ui.role,
                    onRoleChange = viewModel::setRole,
                )
            }
            item {
                OutlinedTextField(
                    value = ui.pin,
                    onValueChange = viewModel::setPin,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Sync PIN (same on all phones)") },
                    singleLine = true,
                )
            }
            item {
                OutlinedButton(onClick = { viewModel.saveSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save PIN")
                }
            }
            if (ui.role == PhoneSyncRole.MASTER) {
                item { MasterSection(ui, viewModel) }
            } else {
                item { SlaveSection(ui, viewModel, context) }
            }
            ui.statusMessage?.let { msg ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(msg, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            ui.lastResult?.let { result ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(result, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowChips(role: PhoneSyncRole, onRoleChange: (PhoneSyncRole) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This phone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = role == PhoneSyncRole.MASTER,
                onClick = { onRoleChange(PhoneSyncRole.MASTER) },
                label = { Text("Master (receiver)") },
            )
            FilterChip(
                selected = role == PhoneSyncRole.SLAVE,
                onClick = { onRoleChange(PhoneSyncRole.SLAVE) },
                label = { Text("Slave (sender)") },
            )
        }
    }
}

@Composable
private fun MasterSection(ui: PhoneSyncUiState, viewModel: PhoneSyncViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Master receiver", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Turn on hotspot on this phone. Slaves connect and use the IP below.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = viewModel::refreshMasterIp, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh IP")
            }
            Text(
                "IP: ${ui.masterIp ?: "—"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Port: ${PhoneSyncProtocol.PORT}", style = MaterialTheme.typography.bodyMedium)
            if (ui.masterRunning) {
                Text("Status: listening for slaves", color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = viewModel::stopMaster, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop receiver")
                }
            } else {
                Button(onClick = viewModel::startMaster, modifier = Modifier.fillMaxWidth()) {
                    Text("Start receiver")
                }
            }
        }
    }
}

@Composable
private fun SlaveSection(ui: PhoneSyncUiState, viewModel: PhoneSyncViewModel, context: android.content.Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Slave sender", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Join master hotspot. Enter master IP, pull catalog, then send only PNGs the master does not have yet.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = ui.masterHost,
                onValueChange = viewModel::setMasterHost,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Master phone IP") },
                placeholder = { Text("e.g. 192.168.43.1") },
                singleLine = true,
            )
            ui.progressLabel?.let {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            OutlinedButton(
                onClick = { viewModel.slavePullCatalog(context) },
                enabled = ui.progressLabel == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("1. Pull master catalog & compare")
            }
            ui.masterCatalogPreview?.let { catalog ->
                Text(
                    "Master: ${catalog.articleCount} articles · ${catalog.imageCount} PNG(s)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Ready to send: ${ui.outboundCount} new PNG(s)",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = { viewModel.slaveSendNewWork(context) },
                enabled = ui.progressLabel == null && ui.outboundCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.progressLabel != null) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                }
                Text("2. Send my new PNGs to master")
            }
        }
    }
}

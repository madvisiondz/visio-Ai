package com.oasismall.oasisai.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun ImportantRayonsScreen(
    viewModel: ImportantRayonsViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rayons importants") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Cochez les rayons Gestium utilisés pour votre travail (étiquettes, photos, VisioPRO). " +
                        "Les autres rayons sont ignorés dans le rapport CSV, les statistiques et les filtres Articles.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::selectVisioProDefaults, modifier = Modifier.fillMaxWidth()) {
                        Text("VisioPRO (F&V, Boucherie, Poisson)")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::selectAll, modifier = Modifier.weight(1f)) {
                            Text("Tout cocher")
                        }
                        OutlinedButton(onClick = viewModel::clearAll, modifier = Modifier.weight(1f)) {
                            Text("Tout décocher")
                        }
                    }
                }
            }
            item {
                Text(
                    "${ui.selectedRayons.size} / ${ui.allRayons.size} rayon(s) sélectionné(s)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (ui.allRayons.isEmpty()) {
                item {
                    Text(
                        "Importez un CSV Gestium pour lister les rayons.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ui.allRayons, key = { it }) { rayon ->
                ListItem(
                    headlineContent = { Text(rayon) },
                    leadingContent = {
                        Checkbox(
                            checked = rayon in ui.selectedRayons,
                            onCheckedChange = { checked -> viewModel.toggleRayon(rayon, checked) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = { viewModel.save(onSaved = onBack) },
                    enabled = ui.selectedRayons.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enregistrer")
                }
            }
        }
    }
}

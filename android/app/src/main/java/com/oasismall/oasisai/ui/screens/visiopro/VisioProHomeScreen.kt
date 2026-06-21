package com.oasismall.oasisai.ui.screens.visiopro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.domain.visiopro.VisioProChannel
import com.oasismall.oasisai.domain.visiopro.VisioProPresetCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisioProHomeScreen(
    onOpenCategory: (VisioProCategory) -> Unit,
    onOpenDesigner: () -> Unit,
    categoryCounts: Map<VisioProCategory, Int> = emptyMap(),
    syncMessage: String? = null,
    onDismissSyncMessage: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbar.showSnackbar(it)
            onDismissSyncMessage()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VisioPRO", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Cartes prix · fruits, légumes, boucherie, poisson",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                ListItem(
                    modifier = Modifier.clickable { onOpenDesigner() },
                    headlineContent = {
                        Text("Studio preset · Designer", fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text("Studio pro — calques, resize, aimant · 7 familles")
                    },
                    leadingContent = {
                        Icon(Icons.Default.Brush, contentDescription = null)
                    },
                )
            }
            items(VisioProCategory.entries.toList()) { category ->
                val socialCount = categoryCounts[category]
                    ?: VisioProPresetCatalog.presets(category, VisioProChannel.SOCIAL).size
                val printCount = categoryCounts[category]
                    ?: VisioProPresetCatalog.presets(category, VisioProChannel.PRINT).size
                val subtitle = when (category) {
                    VisioProCategory.FISH -> "$socialCount articles · réseaux sociaux"
                    else -> "$socialCount articles · $printCount impression"
                }
                ListItem(
                    modifier = Modifier.clickable { onOpenCategory(category) },
                    headlineContent = {
                        Text(
                            "${category.emoji}  ${category.labelFr}",
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    supportingContent = { Text(subtitle) },
                )
            }
            item {
                Text(
                    "Choisissez une catégorie, puis un article pour modifier le prix, la photo (réseaux) ou ajouter à To share.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

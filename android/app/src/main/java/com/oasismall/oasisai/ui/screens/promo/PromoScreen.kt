package com.oasismall.oasisai.ui.screens.promo

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromoScreen(viewModel: PromoViewModel) {
    val batches by viewModel.promoBatches.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Promo Tracker") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (alerts.isNotEmpty()) {
                item { Text("Alerts", style = MaterialTheme.typography.titleMedium) }
                items(alerts, key = { it.id }) { alert ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(alert.message)
                            Button(onClick = { viewModel.dismissAlert(alert.id) }) { Text("Dismiss") }
                        }
                    }
                }
            }
            item { Text("Promo batches", style = MaterialTheme.typography.titleMedium) }
            items(batches, key = { it.id }) { batch ->
                val end = batch.promoEnd
                val expired = end != null && end < System.currentTimeMillis()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(batch.campaignName ?: batch.templateName, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        end?.let {
                            Text(SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(it)))
                        }
                        Text(if (expired) "EXPIRED — needs removal" else "Active", color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

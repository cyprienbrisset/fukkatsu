package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, vm: WeatherSettingsViewModel = viewModel()) {
    var query by remember { mutableStateOf("") }
    val results by vm.results.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Ville météo") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            OutlinedTextField(query, { query = it; vm.search(it) }, label = { Text("Rechercher une ville") })
            LazyColumn {
                items(results) { r ->
                    ListItem(
                        headlineContent = { Text(r.label) },
                        modifier = Modifier.clickable { vm.select(r); onBack() },
                    )
                }
            }
        }
    }
}

package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onTiles: () -> Unit, onAlarms: () -> Unit, onWeather: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Réglages") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
        })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            ListItem(headlineContent = { Text("Tuiles") }, modifier = Modifier.clickable { onTiles() })
            ListItem(headlineContent = { Text("Alarmes") }, modifier = Modifier.clickable { onAlarms() })
            ListItem(headlineContent = { Text("Ville météo") }, modifier = Modifier.clickable { onWeather() })
        }
    }
}

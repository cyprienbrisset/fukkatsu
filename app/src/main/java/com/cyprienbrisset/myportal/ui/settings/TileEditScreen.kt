package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.tile.TileType
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileEditScreen(onBack: () -> Unit, vm: TileEditViewModel = viewModel()) {
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tuiles") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
        }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Ajouter") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(tiles, key = { it.id }) { tile ->
                ListItem(
                    headlineContent = { Text(tile.label) },
                    supportingContent = { Text(tile.packageName ?: tile.url ?: "") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.moveUp(tile) }) { Icon(Icons.Filled.KeyboardArrowUp, "Monter") }
                            IconButton(onClick = { vm.moveDown(tile) }) { Icon(Icons.Filled.KeyboardArrowDown, "Descendre") }
                            IconButton(onClick = { vm.delete(tile) }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd) AddTileDialog(vm = vm, onDismiss = { showAdd = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTileDialog(vm: TileEditViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(TileType.APP) }
    var apps by remember { mutableStateOf<List<com.cyprienbrisset.myportal.launch.InstalledApp>>(emptyList()) }
    var webLabel by remember { mutableStateOf("") }
    var webUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { apps = vm.installedApps() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (mode == TileType.WEB) TextButton(onClick = {
                if (webLabel.isNotBlank() && webUrl.isNotBlank()) { vm.addWeb(webLabel, webUrl); onDismiss() }
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Ajouter une tuile") },
        text = {
            Column {
                Row {
                    FilterChip(selected = mode == TileType.APP, onClick = { mode = TileType.APP }, label = { Text("App") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = mode == TileType.WEB, onClick = { mode = TileType.WEB }, label = { Text("Web") })
                }
                Spacer(Modifier.height(12.dp))
                if (mode == TileType.APP) {
                    Column(Modifier.heightIn(max = 320.dp)) {
                        LazyColumn {
                            items(apps) { a ->
                                ListItem(
                                    headlineContent = { Text(a.label) },
                                    supportingContent = { Text(a.packageName) },
                                    modifier = Modifier.clickable { vm.addApp(a.label, a.packageName); onDismiss() },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(webLabel, { webLabel = it }, label = { Text("Nom") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(webUrl, { webUrl = it }, label = { Text("URL (ex. jellyfin.local)") })
                }
            }
        },
    )
}

package com.cyprienbrisset.myportal.ui.alarms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(onBack: () -> Unit, vm: AlarmsViewModel = viewModel()) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Alarmes") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
        }) },
        floatingActionButton = { FloatingActionButton(onClick = { showPicker = true }) { Icon(Icons.Filled.Add, "Ajouter") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(alarms, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text("%02d:%02d".format(a.hour, a.minute)) },
                    supportingContent = { Text(if (a.repeatDays == 0) "Une fois" else repeatLabel(a.repeatDays)) },
                    trailingContent = {
                        Row {
                            Switch(checked = a.enabled, onCheckedChange = { vm.toggle(a, it) })
                            IconButton(onClick = { vm.delete(a) }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(is24Hour = true)
        var days by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = {
                vm.save(state.hour, state.minute, days, ""); showPicker = false
            }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } },
            title = { Text("Nouvelle alarme") },
            text = {
                Column {
                    TimePicker(state = state)
                    Spacer(Modifier.height(8.dp))
                    DayToggles(days = days, onChange = { days = it })
                }
            },
        )
    }
}

@Composable
private fun DayToggles(days: Int, onChange: (Int) -> Unit) {
    val labels = listOf("L", "M", "M", "J", "V", "S", "D")
    Row {
        labels.forEachIndexed { i, l ->
            FilterChip(
                selected = (days and (1 shl i)) != 0,
                onClick = { onChange(days xor (1 shl i)) },
                label = { Text(l) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

private fun repeatLabel(mask: Int): String {
    val labels = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
    return labels.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(" ")
}

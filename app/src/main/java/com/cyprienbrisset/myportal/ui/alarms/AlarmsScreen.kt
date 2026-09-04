package com.cyprienbrisset.myportal.ui.alarms

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
                    supportingContent = { Text((if (a.repeatDays == 0) "Une fois" else repeatLabel(a.repeatDays)) + " · Snooze ${a.snoozeMinutes}m") },
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
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var ringtoneUri by remember { mutableStateOf<String?>(null) }
        var ringtoneName by remember { mutableStateOf("Par défaut") }
        var snoozeMin by remember { mutableStateOf(10) }
        val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            @Suppress("DEPRECATION")
            val uri = res.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringtoneUri = uri?.toString()
            ringtoneName = uri?.let { RingtoneManager.getRingtone(ctx, it)?.getTitle(ctx) } ?: "Par défaut"
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = {
                vm.save(state.hour, state.minute, days, "", ringtoneUri, snoozeMin); showPicker = false
            }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } },
            title = { Text("Nouvelle alarme") },
            text = {
                Column {
                    TimePicker(state = state)
                    Spacer(Modifier.height(8.dp))
                    DayToggles(days = days, onChange = { days = it })
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sonnerie : $ringtoneName", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonnerie de l'alarme")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let { android.net.Uri.parse(it) })
                            }
                            ringtonePicker.launch(intent)
                        }) { Text("Choisir") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Snooze", style = MaterialTheme.typography.labelMedium)
                    Row {
                        listOf(5, 10, 15).forEach { m ->
                            FilterChip(
                                selected = snoozeMin == m,
                                onClick = { snoozeMin = m },
                                label = { Text("$m min") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
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

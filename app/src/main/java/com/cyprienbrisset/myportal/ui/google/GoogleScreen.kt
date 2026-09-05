package com.cyprienbrisset.myportal.ui.google

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cyprienbrisset.myportal.ui.google.GoogleWebSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileType
import com.cyprienbrisset.myportal.integration.AppShortcuts
import com.cyprienbrisset.myportal.integration.CalEvent
import com.cyprienbrisset.myportal.integration.GoogleApps
import com.cyprienbrisset.myportal.ui.home.TileIcon
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.sumi.SectionLabel
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.OnShu
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiLine
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleScreen(modifier: Modifier = Modifier, vm: GoogleViewModel = viewModel()) {
    val ctx = LocalContext.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val canReadShortcuts by vm.canReadShortcuts.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val loadedOnce by vm.loadedOnce.collectAsStateWithLifecycle()
    val icsUrl by vm.icsUrl.collectAsStateWithLifecycle()

    var webSheetKey by remember { mutableStateOf<String?>(null) }

    var hasCalendarPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCalendarPerm = granted
        if (granted) vm.loadEvents(System.currentTimeMillis())
    }

    LaunchedEffect(Unit) { vm.loadShortcuts() }
    LaunchedEffect(hasCalendarPerm, icsUrl) {
        if (hasCalendarPerm || !icsUrl.isNullOrBlank()) vm.loadEvents(System.currentTimeMillis())
    }

    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("会", size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Text("Google", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }

        webSheetKey?.let { key ->
            val url = if (key == "meet") "https://meet.google.com" else "https://chat.google.com"
            GoogleWebSheet(poolKey = key, url = url, onDismiss = { webSheetKey = null })
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Meet / Chat WebView tiles always visible.
            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel("つながり", "COMMUNICATION")
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WebTile("Meet", onClick = { webSheetKey = "meet" }, modifier = Modifier.weight(1f))
                    WebTile("Chat", onClick = { webSheetKey = "chat" }, modifier = Modifier.weight(1f))
                }
                if (!vm.anyInstalled) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Installe Google Agenda, Chat ou Meet depuis le Store pour les retrouver ici.",
                        color = SumiMuted, fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Long-press app shortcuts, per Google app.
            if (!canReadShortcuts) {
                item { DefaultLauncherHint(onOpenSettings = { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }) }
            }
            items(entries, key = { it.pkg }) { entry ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TileIcon(
                            tile = TileEntity(type = TileType.APP, label = entry.name, packageName = entry.pkg, position = 0),
                            size = 34.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(entry.name, color = Kinari, fontFamily = Mincho, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (entry.shortcuts.isEmpty()) {
                            ShortcutChip("Ouvrir", onClick = { GoogleApps.open(ctx, entry.pkg) })
                        } else {
                            entry.shortcuts.forEach { sc ->
                                ShortcutChip(sc.label, onClick = { AppShortcuts.launch(ctx, sc.pkg, sc.id) })
                            }
                        }
                    }
                }
            }

            // Native live data: upcoming calendar events.
            if (vm.calendarInstalled || !icsUrl.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("よてい", "PROCHAINS ÉVÉNEMENTS")
                    Spacer(Modifier.height(14.dp))
                    when {
                        !hasCalendarPerm && vm.calendarInstalled && icsUrl.isNullOrBlank() -> Column {
                            Text("Autorise l'accès à l'agenda pour afficher tes prochains événements.", color = SumiMuted, fontSize = 15.sp)
                            Spacer(Modifier.height(14.dp))
                            SumiPrimaryButton("Autoriser l'accès", onClick = { permLauncher.launch(Manifest.permission.READ_CALENDAR) })
                        }
                        !hasCalendarPerm && !vm.calendarInstalled && icsUrl.isNullOrBlank() -> Column {
                            Text("Ajoute ton URL d'agenda Google dans les Réglages pour voir tes événements.", color = SumiMuted, fontSize = 15.sp)
                        }
                        loadedOnce && events.isEmpty() ->
                            Text("Aucun événement à venir.", color = SumiMuted, fontSize = 15.sp)
                        else -> {}
                    }
                }
                items(events, key = { "${it.id}-${it.begin}" }) { ev ->
                    EventRow(
                        ev = ev,
                        onOpen = { GoogleApps.openEvent(ctx, ev.id, ev.begin) },
                        onJoin = { ev.meetUrl?.let { GoogleApps.viewUrl(ctx, it) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(SumiSurface)
            .border(1.dp, SumiLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }.padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Kinari, fontFamily = Mincho, fontSize = 20.sp)
    }
}

@Composable
private fun GoogleSignInBanner(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SumiSurface)
            .border(1.dp, SumiLine, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Text("Compte Google", color = Kinari, fontFamily = Mincho, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Connecte ton compte Google pour accéder à Agenda, Meet et Chat avec ton identité.",
            color = SumiMuted, fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        SumiPrimaryButton("Se connecter avec Google", onClick = onSignIn)
    }
}

@Composable
private fun DefaultLauncherHint(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SumiSurface)
            .border(1.dp, SumiLine, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Text("Raccourcis des apps", color = Kinari, fontFamily = Mincho, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Définis Fukkatsu comme launcher par défaut pour afficher et lancer les raccourcis (nouvel événement, nouvelle réunion, conversations…).",
            color = SumiMuted, fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        SumiPrimaryButton("Launcher par défaut", onClick = onOpenSettings)
    }
}

@Composable
private fun ShortcutChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(SumiSurface)
            .border(1.dp, SumiLine, RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label, color = Kinari, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EventRow(ev: CalEvent, onOpen: () -> Unit, onJoin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SumiSurface)
            .clickable { onOpen() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatEventTime(ev.begin, ev.allDay), color = Shu, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(ev.title, color = Kinari, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!ev.location.isNullOrBlank()) {
                Text(ev.location, color = SumiMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (ev.meetUrl != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Shu)
                    .clickable { onJoin() }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Rejoindre", color = OnShu, fontFamily = Mincho, fontSize = 14.sp) }
        }
    }
}

private fun formatEventTime(begin: Long, allDay: Boolean): String {
    val dt = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val today = LocalDate.now()
    val day = dt.toLocalDate()
    val prefix = when (day) {
        today -> "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        else -> dt.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    }
    if (allDay) return "$prefix · journée"
    val hm = "%02d:%02d".format(dt.hour, dt.minute)
    return "$prefix · $hm"
}

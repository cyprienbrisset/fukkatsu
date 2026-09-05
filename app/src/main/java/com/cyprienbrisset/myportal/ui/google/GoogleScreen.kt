package com.cyprienbrisset.myportal.ui.google

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GoogleScreen(modifier: Modifier = Modifier, vm: GoogleViewModel = viewModel()) {
    val ctx = LocalContext.current
    val events by vm.events.collectAsStateWithLifecycle()
    val loadedOnce by vm.loadedOnce.collectAsStateWithLifecycle()

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
    LaunchedEffect(hasCalendarPerm) {
        if (hasCalendarPerm && vm.calendarInstalled) vm.loadEvents(System.currentTimeMillis())
    }

    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("会", size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Text("Google", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }

        val meetPkg = vm.meetPackage
        if (!vm.anyInstalled) {
            Text(
                "Installe Google Agenda, Chat ou Meet depuis le Store pour les retrouver ici.",
                color = SumiMuted, fontSize = 15.sp,
            )
            return@Column
        }

        // Quick access
        SectionLabel("アクセス", "ACCÈS RAPIDE")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (vm.calendarInstalled) {
                QuickCard(GoogleApps.CALENDAR, "Agenda", "Ouvrir") { GoogleApps.open(ctx, GoogleApps.CALENDAR) }
            }
            if (vm.chatInstalled) {
                QuickCard(GoogleApps.CHAT, "Chat", "Ouvrir") { GoogleApps.open(ctx, GoogleApps.CHAT) }
            }
            if (meetPkg != null) {
                QuickCard(meetPkg, "Meet", "Démarrer") { GoogleApps.startMeet(ctx) }
            }
        }

        if (vm.calendarInstalled) {
            Spacer(Modifier.height(26.dp))
            SectionLabel("よてい", "PROCHAINS ÉVÉNEMENTS")
            Spacer(Modifier.height(14.dp))
            when {
                !hasCalendarPerm -> {
                    Text(
                        "Autorise l'accès à l'agenda pour afficher tes prochains événements.",
                        color = SumiMuted, fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    SumiPrimaryButton("Autoriser l'accès", onClick = {
                        permLauncher.launch(Manifest.permission.READ_CALENDAR)
                    })
                }
                loadedOnce && events.isEmpty() -> {
                    Text("Aucun événement à venir.", color = SumiMuted, fontSize = 15.sp)
                    Spacer(Modifier.height(14.dp))
                    SumiPrimaryButton("Ouvrir l'agenda", onClick = { GoogleApps.open(ctx, GoogleApps.CALENDAR) })
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
}

@Composable
private fun QuickCard(pkg: String, label: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).background(SumiSurface)
            .clickable { onClick() }.padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TileIcon(tile = TileEntity(type = TileType.APP, label = label, packageName = pkg, position = 0), size = 52.dp)
        Spacer(Modifier.height(12.dp))
        Text(label, color = Kinari, fontSize = 17.sp)
        Spacer(Modifier.height(4.dp))
        Text(action, color = Shu, fontFamily = Mincho, fontSize = 13.sp)
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

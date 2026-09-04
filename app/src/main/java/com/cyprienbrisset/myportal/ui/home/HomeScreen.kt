package com.cyprienbrisset.myportal.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileType
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver
import com.cyprienbrisset.myportal.system.DndController
import com.cyprienbrisset.myportal.system.ScreenLock
import com.cyprienbrisset.myportal.ui.sumi.SealIconButton
import com.cyprienbrisset.myportal.ui.sumi.SectionLabel
import com.cyprienbrisset.myportal.ui.sumi.VerticalVermilionRule
import com.cyprienbrisset.myportal.ui.sumi.WatermarkKanji
import com.cyprienbrisset.myportal.web.WebAppActivity

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onAddTile: () -> Unit, vm: HomeViewModel = viewModel()) {
    val ctx = LocalContext.current
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val nextAlarm by vm.nextAlarm.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()

    val launch: (TileEntity) -> Unit = { tile ->
        when (tile.type) {
            TileType.APP -> {
                val pkg = tile.packageName
                if (pkg == null || !LaunchIntentResolver.launch(ctx, pkg))
                    Toast.makeText(ctx, "App introuvable : ${tile.label}", Toast.LENGTH_SHORT).show()
            }
            TileType.WEB -> ctx.startActivity(
                Intent(ctx, WebAppActivity::class.java).putExtra(WebAppActivity.EXTRA_URL, tile.url)
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        LaunchedEffect(now) { vm.refreshNowPlaying() }
        WatermarkKanji("墨", Modifier.align(Alignment.BottomEnd).offset(x = (-64).dp, y = (-10).dp))
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(start = 46.dp, top = 44.dp, bottom = 40.dp, end = 90.dp)) {
                Box(Modifier.fillMaxHeight().weight(0.38f)) {
                    HomeBranding(portrait = false, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                    Column(
                        Modifier.align(Alignment.Center).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AmbientBanner(now, weather, nextAlarm = nextAlarm, portrait = false)
                        val np = nowPlaying
                        if (np != null) {
                            Spacer(Modifier.height(24.dp))
                            NowPlayingBar(np, onPrev = { vm.mediaPrev() }, onToggle = { vm.mediaToggle() }, onNext = { vm.mediaNext() })
                        }
                    }
                }
                VerticalVermilionRule(Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp), length = 220.dp)
                Column(Modifier.fillMaxHeight().weight(0.62f).padding(start = 30.dp), verticalArrangement = Arrangement.Center) {
                    SectionLabel("アプリ", "MES APPS")
                    Spacer(Modifier.height(22.dp))
                    MedallionGrid(tiles, minCellWidth = 108.dp, onTileClick = launch, onAddClick = onAddTile)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(20.dp))
                HomeBranding(portrait = true)
                Spacer(Modifier.height(24.dp))
                AmbientBanner(now, weather, nextAlarm = nextAlarm, portrait = true)
                val np = nowPlaying
                if (np != null) {
                    Spacer(Modifier.height(18.dp))
                    NowPlayingBar(np, onPrev = { vm.mediaPrev() }, onToggle = { vm.mediaToggle() }, onNext = { vm.mediaNext() })
                }
                Spacer(Modifier.height(28.dp))
                SectionLabel("アプリ", "MES APPS")
                Spacer(Modifier.height(18.dp))
                MedallionGrid(tiles, minCellWidth = 104.dp, onTileClick = launch, onAddClick = onAddTile, modifier = Modifier.weight(1f))
            }
        }

        // Cluster drawn LAST so it sits above the scrollable medallion grid and actually receives taps.
        val dndOn = remember(now) { DndController.isGranted(ctx) && DndController.isDndOn(ctx) }
        Row(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 40.dp, end = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SealIconButton(
                icon = if (dndOn) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                contentDescription = "Ne pas déranger",
                active = dndOn,
                onClick = { DndController.toggleOrRequest(ctx) },
            )
            SealIconButton(
                icon = Icons.Rounded.PowerSettingsNew,
                contentDescription = "Éteindre l'écran",
                onClick = { ScreenLock.lockOrRequest(ctx) },
            )
            SealIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Réglages",
                onClick = onOpenSettings,
            )
        }
    }
}

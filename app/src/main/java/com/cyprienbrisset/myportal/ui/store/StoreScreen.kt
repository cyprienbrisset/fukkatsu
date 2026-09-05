package com.cyprienbrisset.myportal.ui.store

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver
import com.cyprienbrisset.myportal.store.FukkaLoginActivity
import com.cyprienbrisset.myportal.store.StoreApp
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.sumi.SectionLabel
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiLine
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

/**
 * @param showBack when true the header seal navigates back (standalone screen from Settings);
 *   when false the seal is a decorative-only mark (embedded as the "Store" tab of the home shell).
 */
@Composable
fun StoreScreen(onBack: () -> Unit, showBack: Boolean = true, vm: StoreViewModel = viewModel()) {
    val ctx = LocalContext.current
    val loggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    LaunchedEffect(loggedIn) { if (loggedIn) vm.loadHome() }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("店", size = 40.dp, onClick = if (showBack) onBack else null)
            Spacer(Modifier.width(14.dp))
            Text("FukkaStore", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }

        if (!loggedIn) {
            Text(
                "Connectez-vous à votre compte Google pour rechercher et installer des applications.",
                color = SumiMuted,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(20.dp))
            SumiPrimaryButton("Se connecter", onClick = {
                ctx.startActivity(Intent(ctx, FukkaLoginActivity::class.java))
            })
            return@Column
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Rechercher une application", color = SumiMuted) },
            )
            Spacer(Modifier.width(12.dp))
            SumiPrimaryButton("Rechercher", onClick = { vm.search(query) })
        }
        Spacer(Modifier.height(18.dp))

        // A search is "active" whenever ui is not Idle; otherwise we show the curated home.
        val searching = ui !is StoreUi.Idle
        if (searching) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("けんさく", "RÉSULTATS", modifier = Modifier.weight(1f))
                Text(
                    "Effacer",
                    color = Shu,
                    fontFamily = Mincho,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { query = ""; vm.clearSearch() },
                )
            }
        } else {
            SectionLabel("おすすめ", "POPULAIRES")
        }
        Spacer(Modifier.height(14.dp))

        AppResults(
            state = if (searching) ui else home,
            progress = progress,
            onInstall = { vm.install(it) },
            onOpen = { LaunchIntentResolver.launch(ctx, it.packageName) },
        )
    }
}

@Composable
private fun AppResults(
    state: StoreUi,
    progress: Map<String, Int>,
    onInstall: (StoreApp) -> Unit,
    onOpen: (StoreApp) -> Unit,
) {
    when (state) {
        is StoreUi.Idle -> {}
        is StoreUi.Loading -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Shu)
            }
        }
        is StoreUi.Error -> Text(state.message, color = SumiMuted, fontSize = 15.sp)
        is StoreUi.Results -> {
            if (state.apps.isEmpty()) {
                Text("Aucune application compatible.", color = SumiMuted, fontSize = 15.sp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    items(state.apps, key = { it.packageName }) { app ->
                        AppCard(
                            app = app,
                            pct = progress[app.packageName],
                            onInstall = { onInstall(app) },
                            onOpen = { onOpen(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: StoreApp, pct: Int?, onInstall: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SumiSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = app.iconUrl,
            contentDescription = app.title,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.title, color = Kinari, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.developer, color = SumiMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        when {
            pct == null -> Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Shu)
                    .clickable { onInstall() }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Installer", color = com.cyprienbrisset.myportal.ui.theme.OnShu, fontFamily = Mincho, fontSize = 14.sp) }
            pct in 0..99 -> Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SumiLine).padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("$pct %", color = Kinari, fontSize = 14.sp) }
            pct == InstallProgress.INSTALLING -> Text("Installation…", color = SumiMuted, fontSize = 14.sp)
            pct == InstallProgress.INSTALLED -> Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SumiLine)
                    .clickable { onOpen() }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Ouvrir", color = Kinari, fontFamily = Mincho, fontSize = 14.sp) }
            else -> Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SumiSurface)
                    .clickable { onInstall() }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("Réessayer", color = Shu, fontFamily = Mincho, fontSize = 14.sp) }
        }
    }
}

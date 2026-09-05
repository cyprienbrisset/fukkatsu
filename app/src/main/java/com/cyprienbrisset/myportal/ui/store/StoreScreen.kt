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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cyprienbrisset.myportal.store.FukkaLoginActivity
import com.cyprienbrisset.myportal.store.StoreApp
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun StoreScreen(onBack: () -> Unit, vm: StoreViewModel = viewModel()) {
    val ctx = LocalContext.current
    val loggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("店", size = 40.dp, onClick = onBack)
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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        SumiPrimaryButton("Rechercher", onClick = { vm.search(query) })
        Spacer(Modifier.height(20.dp))

        when (val state = ui) {
            is StoreUi.Idle -> {}
            is StoreUi.Loading -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Shu)
                }
            }
            is StoreUi.Error -> {
                Text(state.message, color = SumiMuted, fontSize = 15.sp)
            }
            is StoreUi.Results -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.apps) { app ->
                        AppRow(app = app, pct = progress[app.packageName], onInstall = { vm.install(app) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: StoreApp, pct: Int?, onInstall: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = app.iconUrl,
            contentDescription = app.title,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.title, color = Kinari, fontSize = 16.sp)
            Text(app.developer, color = SumiMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        when {
            pct == null -> {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(SumiSurface)
                        .clickable { onInstall() }.padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text("Installer", color = Kinari, fontFamily = Mincho, fontSize = 14.sp)
                }
            }
            pct in 0..99 -> Text("$pct %", color = SumiMuted, fontSize = 14.sp)
            pct == 100 -> Text("Installation…", color = SumiMuted, fontSize = 14.sp)
            else -> Text("Échec", color = Shu, fontSize = 14.sp)
        }
    }
}

package com.cyprienbrisset.myportal.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.tile.TileType
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        AmbientBanner(now = now, weather = weather)
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Réglages")
        }
        TileGrid(tiles = tiles, onTileClick = { tile ->
            when (tile.type) {
                TileType.APP -> {
                    val pkg = tile.packageName
                    if (pkg == null || !LaunchIntentResolver.launch(ctx, pkg)) {
                        Toast.makeText(ctx, "App introuvable : ${tile.label}", Toast.LENGTH_SHORT).show()
                    }
                }
                TileType.WEB -> {
                    ctx.startActivity(
                        android.content.Intent(ctx, com.cyprienbrisset.myportal.web.WebAppActivity::class.java)
                            .putExtra(com.cyprienbrisset.myportal.web.WebAppActivity.EXTRA_URL, tile.url)
                    )
                }
            }
        })
    }
}

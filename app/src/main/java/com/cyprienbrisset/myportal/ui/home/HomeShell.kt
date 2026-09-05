package com.cyprienbrisset.myportal.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.ui.store.StoreScreen
import com.cyprienbrisset.myportal.ui.sumi.Segment
import com.cyprienbrisset.myportal.ui.sumi.SegmentedChoice

/**
 * The home is a two-tab shell: the ambient launcher ("Accueil") and the integrated store ("Store").
 * A Sumi segmented control at the bottom switches between them. Kana on the segments is decorative;
 * the French labels are the functional ones.
 */
@Composable
fun HomeShell(onOpenSettings: () -> Unit, onAddTile: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = tab, label = "home-tab") { t ->
                when (t) {
                    0 -> HomeScreen(onOpenSettings = onOpenSettings, onAddTile = onAddTile)
                    else -> StoreScreen(onBack = { tab = 0 }, showBack = false)
                }
            }
        }
        SegmentedChoice(
            options = listOf(Segment("かん", "Accueil"), Segment("みせ", "Store")),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
        )
    }
}

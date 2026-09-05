package com.cyprienbrisset.myportal.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.cyprienbrisset.myportal.ui.google.GoogleScreen
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.OnShu
import com.cyprienbrisset.myportal.ui.theme.Shu

/**
 * The home is a two-tab shell: the ambient launcher ("Accueil") and the Google integration page
 * ("Google"). A prominent Sumi tab bar floats above the bottom edge. Kana is decorative; the
 * French labels are functional.
 */
@Composable
fun HomeShell(onOpenSettings: () -> Unit, onAddTile: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = tab, label = "home-tab", modifier = Modifier.fillMaxSize()) { t ->
            when (t) {
                0 -> HomeScreen(onOpenSettings = onOpenSettings, onAddTile = onAddTile)
                else -> GoogleScreen()
            }
        }
        HomeTabBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp),
        )
    }
}

@Composable
private fun HomeTabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .widthIn(max = 520.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HomeTab("かん", "Accueil", on = selected == 0, onClick = { onSelect(0) }, modifier = Modifier.weight(1f))
        HomeTab("グ", "Google", on = selected == 1, onClick = { onSelect(1) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HomeTab(kana: String, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(15.dp))
            .background(if (on) Shu else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(kana, fontFamily = Mincho, color = if (on) OnShu else Shu, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontFamily = Mincho,
            fontWeight = FontWeight.Medium,
            color = if (on) OnShu else MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
        )
    }
}

package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.media.NowPlaying
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun NowPlayingBar(np: NowPlaying, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(max = 420.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(SumiSurface), contentAlignment = Alignment.Center) {
            val art = np.art
            if (art != null) Image(art.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.widthIn(min = 0.dp).weight(1f)) {
            Text(np.title.ifBlank { "Lecture" }, color = Kinari, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (np.artist.isNotBlank()) Text(np.artist, color = SumiMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.SkipPrevious, "Précédent", tint = Kinari, modifier = Modifier.size(30.dp).clickable { onPrev() })
        Icon(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", tint = Kinari,
            modifier = Modifier.size(38.dp).clickable { onToggle() })
        Icon(Icons.Rounded.SkipNext, "Suivant", tint = Kinari, modifier = Modifier.size(30.dp).clickable { onNext() })
    }
}

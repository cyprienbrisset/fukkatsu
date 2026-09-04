package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
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
import com.cyprienbrisset.myportal.ui.theme.Ink2
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.OnShu
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun NowPlayingBar(
    np: NowPlaying,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SumiSurface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(Ink2),
            contentAlignment = Alignment.Center,
        ) {
            val art = np.art
            if (art != null) {
                Image(art.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = SumiMuted, modifier = Modifier.size(36.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                np.title.ifBlank { "Lecture en cours" },
                color = Kinari, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (np.artist.isNotBlank()) {
                Text(np.artist, color = SumiMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            Icons.Rounded.SkipPrevious, "Précédent", tint = Kinari,
            modifier = Modifier.size(42.dp).clickable { onPrev() },
        )
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(Shu).clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                "Play/Pause", tint = OnShu, modifier = Modifier.size(34.dp),
            )
        }
        Icon(
            Icons.Rounded.SkipNext, "Suivant", tint = Kinari,
            modifier = Modifier.size(42.dp).clickable { onNext() },
        )
    }
}

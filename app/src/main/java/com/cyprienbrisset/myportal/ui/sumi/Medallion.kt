package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiLine
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun Medallion(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    discSize: Dp = 72.dp,
    focused: Boolean = false,
    dashed: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        val disc = Modifier.size(discSize).clip(CircleShape)
            .background(if (dashed) Color.Transparent else SumiSurface)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Shu else SumiLine), CircleShape)
        Box(disc, contentAlignment = Alignment.Center) { content() }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Kinari, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

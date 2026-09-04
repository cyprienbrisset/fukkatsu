package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.OnShu
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiLine
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun SumiChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, circle: Boolean = false) {
    val shape: Shape = if (circle) CircleShape else RoundedCornerShape(14.dp)
    Box(
        modifier.height(if (circle) 52.dp else 56.dp).clip(shape)
            .background(if (selected) Shu else SumiSurface)
            .border(BorderStroke(1.dp, if (selected) Shu else SumiLine), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) OnShu else Kinari, fontSize = if (circle) 15.sp else 16.sp)
    }
}

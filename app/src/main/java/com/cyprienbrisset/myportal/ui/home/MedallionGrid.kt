package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.ui.sumi.Medallion

@Composable
fun MedallionGrid(
    tiles: List<TileEntity>,
    minCellWidth: Dp,
    onTileClick: (TileEntity) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(tiles, key = { it.id }) { tile ->
            Medallion(label = tile.label, onClick = { onTileClick(tile) }, disc = false) {
                TileIcon(tile = tile, size = 64.dp)
            }
        }
        item(key = "__add__") {
            Medallion(label = "Ajouter", onClick = onAddClick, dashed = true) {
                Text("＋", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

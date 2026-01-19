package com.urlradiodroid.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urlradiodroid.R
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.ui.theme.glass_accent
import com.urlradiodroid.ui.theme.glass_primary
import com.urlradiodroid.util.EmojiGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationItem(
    station: RadioStation,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> false
                SwipeToDismissBoxValue.EndToStart -> false
                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )
    val coroutineScope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        backgroundContent = {
            SwipeActionsBackground(
                onEditClick = {
                    coroutineScope.launch {
                        dismissState.reset()
                    }
                    onEditClick()
                },
                onDeleteClick = {
                    coroutineScope.launch {
                        dismissState.reset()
                    }
                    onDeleteClick()
                }
            )
        },
        content = {
            StationCard(
                station = station,
                isPlaying = isPlaying,
                onPlayClick = {
                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                        coroutineScope.launch {
                            dismissState.reset()
                        }
                    }
                    onPlayClick()
                },
                onCardClick = {
                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                        coroutineScope.launch {
                            dismissState.reset()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionsBackground(
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onEditClick,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onError
            )
        }
    }
}

@Composable
private fun StationCard(
    station: RadioStation,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(
            containerColor = glass_primary
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = station.customIcon
                    ?: EmojiGenerator.getEmojiForStation(station.name, station.streamUrl),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.size(36.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = station.streamUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            PlayPauseIcon(
                isPlaying = isPlaying,
                onClick = onPlayClick,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun PlayPauseIcon(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconRes = if (isPlaying) {
        R.drawable.ic_stop_circle
    } else {
        R.drawable.ic_play_circle
    }

    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = if (isPlaying) "Stop" else "Play",
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(glass_accent)
        )
    }
}

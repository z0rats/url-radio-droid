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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urlradiodroid.R
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.ui.theme.glass_accent
import com.urlradiodroid.ui.theme.text_hint
import com.urlradiodroid.ui.theme.text_primary
import com.urlradiodroid.util.EmojiGenerator

@Composable
fun NowPlayingBottomBar(
    station: RadioStation?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (station == null) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = station.customIcon
                    ?: EmojiGenerator.getEmojiForStation(station.name, station.streamUrl),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = text_primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isPlaying) {
                        stringResource(R.string.playing)
                    } else {
                        stringResource(R.string.paused)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = text_hint
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onPlayPauseClick)
            ) {
                val iconRes = if (isPlaying) {
                    R.drawable.ic_stop_circle
                } else {
                    R.drawable.ic_play_circle
                }
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = if (isPlaying) stringResource(R.string.stop) else stringResource(R.string.play),
                    modifier = Modifier.size(48.dp),
                    colorFilter = ColorFilter.tint(glass_accent)
                )
            }
        }
    }
}

package com.psimandan.neuread.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.ui.player.PlayerViewModel

@Composable
fun MiniPlayerView(
    book: NeuReadBook?,
    uiState: PlayerViewModel.PlayerUIState,
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit, // Added handler for rewind
    onForwardClick: () -> Unit, // Added handler for fast forward
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (book == null) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp, vertical = 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (uiState.sliderRange.endInclusive > 0)
                        uiState.progress / uiState.sliderRange.endInclusive
                    else 0f
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = " • ${uiState.progressTime} / ${uiState.totalTimeString}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // Wrapped the buttons in a Row to display them side-by-side
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Fast Rewind Button
                    IconButton(onClick = onRewindClick) {
                        Icon(
                            imageVector = Icons.Filled.FastRewind,
                            contentDescription = "Rewind",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Play/Pause Button
                    IconButton(onClick = onPlayPauseClick) {
                        Icon(
                            imageVector = if (uiState.isSpeaking) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (uiState.isSpeaking) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Fast Forward Button
                    IconButton(onClick = onForwardClick) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = "Fast Forward",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
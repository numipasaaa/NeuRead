package com.psimandan.neuread.ui.library.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.voice.toLocale


@Composable
fun BookItemView(
    item: NeuReadBook,
    downloadProgress: Float?,
    isCurrentlyPlaying: Boolean = false, // ADDED
    isPlaying: Boolean = false, // ADDED
    realTimeProgress: String? = null, // ADDED
    onSelect: () -> Unit
) {
    val uiState by item.viewState.collectAsState()

    // Animate scale up slightly when it's the currently playing book
    val scale by animateFloatAsState(targetValue = if (isCurrentlyPlaying) 1.02f else 1f, label = "scale")

    LaunchedEffect(item.id) {
        item.lazyCalculate { }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            // Highlight the background if active
            containerColor = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        // Add a colored border if active
        border = if (isCurrentlyPlaying) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentlyPlaying) 8.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // NEW: "Currently Playing" / "Last Played" Badge
            if (isCurrentlyPlaying) {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.VolumeUp else Icons.Filled.PlayArrow,
                        contentDescription = "Playing Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "Currently Listening" else "Last Played",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (downloadProgress != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        )
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isCalculating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    // Use realTimeProgress if this is the active book, otherwise fallback to saved progress
                    val activeProgress = if (isCurrentlyPlaying && realTimeProgress != null) realTimeProgress else uiState.progressTime

                    val progressText = if (uiState.isCompleted) {
                        "Completed"
                    } else {
                        "$activeProgress / ${uiState.totalTime}"
                    }

                    Surface(
                        color = if (uiState.isCompleted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        else if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = progressText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.isCompleted) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = item.language.toLocale().displayLanguage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBookItemView() {
    NeuReadTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            BookItemView(
                item = NeuReadBook.sampleBooks().first().apply {
                    lazyCalculate {}
                },
                downloadProgress = 0.5f,
                onSelect = { println("Book selected") }
            )
            HorizontalDivider()
            BookItemView(
                item = NeuReadBook.sampleBooks()[1],
                downloadProgress = null,
                onSelect = { println("Book selected") }
            )
            HorizontalDivider()
            BookItemView(
                item = NeuReadBook.sampleBooks().last(),
                downloadProgress = null,
                onSelect = { println("Book selected") }
            )
        }

    }
}

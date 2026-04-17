package com.psimandan.neuread.ui.player.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.psimandan.neuread.ui.theme.OpenDyslexic
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.psimandan.neuread.data.model.Bookmark
import com.psimandan.neuread.ui.player.PlayerEvent
import com.psimandan.neuread.ui.player.PlayerViewModel
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.ui.theme.normalSpace
import com.psimandan.neuread.ui.theme.smallSpace

@Preview(showBackground = true)
@Composable
fun BookmarksSectionViewPreview() {
    NeuReadTheme(darkTheme = true) {
        BookmarksSectionView(uiState = PlayerViewModel.PlayerUIState(
            bookmarks = listOf(
                Bookmark(1, "Test 1 Test 1 Test 1 Test 1 Test 1 Test 1  Test 1"),
                Bookmark(2, "Test 2"),
                Bookmark(3, "Test 3"),
                Bookmark(4, "Test 4"),
                Bookmark(5, "Test 5"),
                Bookmark(6, "Test 6"),
                Bookmark(7, "Test 7"),
                Bookmark(8, "Test 8"),
                Bookmark(9, "Test 9"),
            ),
            isSpeaking = true,
            progressTime = "00:00",
            progress = 100f,
            totalTimeString = "00:00"
        ), onEvent = {})
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarksSectionView(
    uiState: PlayerViewModel.PlayerUIState,
    onEvent: (PlayerEvent) -> Unit,
) {
    var selectedBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkToEditNote by remember { mutableStateOf<Bookmark?>(null) }

    if (bookmarkToEditNote != null) {
        var noteText by remember { mutableStateOf(bookmarkToEditNote?.note ?: "") }
        Dialog(onDismissRequest = { bookmarkToEditNote = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Edit Note",
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { bookmarkToEditNote = null }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            bookmarkToEditNote?.let {
                                onEvent(PlayerEvent.UpdateBookmarkNote(it, noteText))
                            }
                            bookmarkToEditNote = null
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = normalSpace)) {
        Text(
            text = "Bookmarks",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = smallSpace)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            items(
                items = uiState.bookmarks.sortedByDescending { it.position },
                key = { it.position }
            ) { bookMark ->
                var showDelete by remember { mutableStateOf(false) }
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = {
                                onEvent(PlayerEvent.BookmarkClick(bookMark.position.toFloat()))
                            },
                            onLongClick = {
                                if (showDelete && selectedBookmark == bookMark) {
                                    showDelete = false
                                    selectedBookmark = null
                                } else {
                                    selectedBookmark = bookMark
                                    showDelete = true
                                }
                            }
                        ),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookMark.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = if (uiState.isDyslexicFontEnabled) OpenDyslexic else FontFamily.Default
                                ),
                                color = colorScheme.onSurface
                            )
                            if (!bookMark.note.isNullOrBlank()) {
                                Text(
                                    text = bookMark.note!!,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = if (uiState.isDyslexicFontEnabled) OpenDyslexic else FontFamily.Default
                                    ),
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (showDelete && selectedBookmark == bookMark) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        bookmarkToEditNote = bookMark
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Note",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        onEvent(PlayerEvent.DeleteBookmark(bookMark))
                                        showDelete = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else if (!bookMark.note.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.Notes,
                                contentDescription = "Has Note",
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
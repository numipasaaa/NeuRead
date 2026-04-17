package com.psimandan.neuread.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.model.Bookmark
import com.psimandan.neuread.data.model.Chapter
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.ui.player.components.BookmarksSectionView
import com.psimandan.neuread.ui.player.components.HorizontallyScrolledTextView
import com.psimandan.neuread.ui.components.NiceRoundButton
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.ui.theme.OpenDyslexic
import com.psimandan.neuread.ui.theme.doubleLargeSpace
import com.psimandan.neuread.ui.theme.largeSpace
import com.psimandan.neuread.ui.theme.normalSpace
import com.psimandan.neuread.ui.theme.smallSpace
import com.psimandan.neuread.voice.toLocale
import kotlinx.coroutines.launch
import java.util.Locale

@Preview(showBackground = true)
@Composable
fun PlayerScreenPreview() {
    NeuReadTheme(darkTheme = true) {
        PlayerScreenContent(
            selectedBook = NeuReadBook.sampleBooks().first(),
            uiState = PlayerViewModel.PlayerUIState(
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
                    Bookmark(9, "Test 9"),
                    Bookmark(9, "Test 9"),
                    Bookmark(9, "Test 9"),
                )
            ),
            currentFrame = listOf("Hello", "Compose", "Preview", "How", "Are", "You", "Doing", "Today"),
            currentWordIndexInFrame = 1,
            sliderRange = 0f..100f,
            onEvent = {}
        )
    }
}

@Composable
fun PlayerScreenView(
    onBackToLibrary: () -> Unit,
    onNavigateToSettings: (NeuReadBook) -> Unit,
    viewModel: PlayerViewModel,
    playbackProgressCallBack: (Float) -> Unit
) {
    val uiState by viewModel.viewState.collectAsState()
    val highlightState by viewModel.highlightingState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setUpBook()
    }

    PlayerScreenContent(
        selectedBook = viewModel.book,
        uiState = uiState,
        currentFrame = highlightState.currentFrame,
        currentWordIndexInFrame = highlightState.currentWordIndexInFrame,
        sliderRange = uiState.sliderRange,
        onEvent = { event ->
            when (event) {
                PlayerEvent.BackToLibrary -> onBackToLibrary()
                PlayerEvent.PauseClick -> viewModel.onPause()
                PlayerEvent.PlayClick -> viewModel.onPlay()
                PlayerEvent.FastForward -> viewModel.fastForward()
                PlayerEvent.FastRewind -> viewModel.fastRewind()
                PlayerEvent.AddBookmark -> viewModel.saveBookmark()
                is PlayerEvent.ChapterClick -> viewModel.jumpToChapter(event.chapter)
                is PlayerEvent.SliderValueChange -> {
                    viewModel.onSliderValueChange(event.value)
                    playbackProgressCallBack(event.value)
                }

                PlayerEvent.Settings -> viewModel.book?.let { onNavigateToSettings(it) }
                PlayerEvent.ToggleExtendedTextMode -> viewModel.toggleExtendedTextMode()
                is PlayerEvent.UpdateBookmarkNote -> viewModel.updateBookmarkNote(
                    event.bookmark,
                    event.note
                )
                is PlayerEvent.BookmarkClick -> {
                    viewModel.playFromBookmark(event.position.toInt())
                    playbackProgressCallBack(event.position)
                }
                is PlayerEvent.SetSleepTimer -> viewModel.setSleepTimer(event.minutes)
                PlayerEvent.CancelSleepTimer -> viewModel.cancelSleepTimer()
                is PlayerEvent.DeleteBookmark -> viewModel.deleteBookmark(event.bookmark)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreenContent(
    selectedBook: NeuReadBook?,
    uiState: PlayerViewModel.PlayerUIState,
    currentFrame: List<String>,
    currentWordIndexInFrame: Int,
    sliderRange: ClosedFloatingPointRange<Float>,
    onEvent: (PlayerEvent) -> Unit
) {
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Box {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { showSleepTimerSheet = true }) {
                                Text(
                                    if (uiState.sleepTimerSecondsRemaining != null) {
                                        val minutes = uiState.sleepTimerSecondsRemaining / 60
                                        val seconds = uiState.sleepTimerSecondsRemaining % 60
                                        java.lang.String.format(
                                            java.util.Locale.getDefault(),
                                            "%02d:%02d",
                                            minutes,
                                            seconds
                                        )
                                    } else "Sleep",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = { onEvent(PlayerEvent.BackToLibrary) }) {
                            Text(
                                "Library",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { onEvent(PlayerEvent.Settings) }) {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.primary
                            )
                        }
                    }
                )
            },
            content = { padding ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (uiState.isExtendedTextMode) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(normalSpace)
                                .background(
                                    colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(normalSpace)
                        ) {
                            val scrollState = rememberScrollState()
                            var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                            val primaryColor = colorScheme.primary
                            val onPrimaryColor = colorScheme.onPrimary
                            val onSurfaceColor = colorScheme.onSurface
                            val annotatedString = remember(currentFrame, currentWordIndexInFrame, uiState.isHighlightingEnabled, primaryColor, onPrimaryColor, onSurfaceColor) {
                                buildAnnotatedString {
                                    currentFrame.forEachIndexed { index, word ->
                                        val isHighlighted = uiState.isHighlightingEnabled && index == currentWordIndexInFrame
                                        if (isHighlighted) {
                                            withStyle(
                                                style = SpanStyle(
                                                    background = primaryColor,
                                                    color = onPrimaryColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            ) {
                                                append(word)
                                            }
                                        } else {
                                            withStyle(
                                                style = SpanStyle(
                                                    background = Color.Transparent,
                                                    color = onSurfaceColor
                                                )
                                            ) {
                                                append(word)
                                            }
                                        }
                                        if (index < currentFrame.size - 1) {
                                            append(" ")
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(currentWordIndexInFrame, uiState.isSpeaking) {
                                if (currentWordIndexInFrame == 0 && uiState.isSpeaking) {
                                    scrollState.animateScrollTo(0)
                                } else if (uiState.isSpeaking) {
                                    textLayoutResult?.let { layoutResult ->
                                        // Calculate the start offset of the current word in the annotated string
                                        var charOffset = 0
                                        for (i in 0 until currentWordIndexInFrame) {
                                            charOffset += currentFrame[i].length + 1 // +1 for the space
                                        }

                                        // Get the bounding box (rect) for the first character of the current word
                                        val cursorRect = layoutResult.getCursorRect(charOffset)
                                        val wordTop = cursorRect.top
                                        val wordBottom = cursorRect.bottom

                                        val viewportHeight = scrollState.viewportSize
                                        if (viewportHeight > 0) {
                                            val currentScroll = scrollState.value
                                            val buffer = 50 // Padding to keep the word from being exactly at the edge

                                            if (wordTop < currentScroll + buffer) {
                                                scrollState.animateScrollTo((wordTop - buffer).toInt().coerceAtLeast(0))
                                            } else if (wordBottom > currentScroll + viewportHeight - buffer) {
                                                scrollState.animateScrollTo((wordBottom - viewportHeight + buffer).toInt())
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = annotatedString,
                                onTextLayout = { textLayoutResult = it },
                                modifier = Modifier.verticalScroll(scrollState),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = if (uiState.isDyslexicFontEnabled) OpenDyslexic else FontFamily.Default,
                                    lineHeight = 22.sp
                                ),
                                color = colorScheme.onSurface
                            )
                        }
                    } else {
                        if (uiState.bookmarks.isNotEmpty()) {
                            BookmarksSectionView(uiState, onEvent)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }
            },
            bottomBar = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    selectedBook?.let {
                        Spacer(modifier = Modifier.padding(vertical = normalSpace))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = normalSpace)
                        ) {
                            Text(
                                it.title,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                it.author,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Column(
                            modifier = Modifier.padding(
                                horizontal = doubleLargeSpace,
                                vertical = largeSpace
                            )
                        ) {
                            Slider(
                                value = uiState.progress,
                                valueRange = sliderRange,
                                onValueChange = { value ->
                                    onEvent(PlayerEvent.SliderValueChange(value))
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = colorScheme.primary,
                                    activeTrackColor = colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row {
                                Text(
                                    text = uiState.progressTime,
                                    maxLines = 1,
                                    color = colorScheme.tertiary,
                                    fontWeight = FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = uiState.totalTimeString,
                                    maxLines = 1,
                                    color = colorScheme.tertiary,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    } ?: run {
                        if (!uiState.isSpeaking) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Loading..", textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                    }
                    if (!uiState.isExtendedTextMode) {
                        HorizontalDivider()
                        HorizontallyScrolledTextView(
                            highLight = uiState.isHighlightingEnabled,
                            words = currentFrame,
                            index = currentWordIndexInFrame,
                            language = selectedBook?.language?.toLocale() ?: Locale.getDefault(),
                            isDyslexicFontEnabled = uiState.isDyslexicFontEnabled
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(largeSpace),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NiceRoundButton(
                            contentDescription = "Fast Rewind",
                            icon = Icons.Filled.FastRewind,
                            diameter = 44.dp,
                            clickHandler = { onEvent(PlayerEvent.FastRewind) }
                        )
                        Spacer(modifier = Modifier.width(smallSpace))

                        Box(contentAlignment = Alignment.Center) {
                            NiceRoundButton(
                                contentDescription = "Play and Pause",
                                icon = if (uiState.isSpeaking) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                backgroundColor = colorScheme.primary,
                                diameter = 64.dp,
                                scale = 2f,
                                clickHandler = {
                                    if (uiState.isSpeaking)
                                        onEvent(PlayerEvent.PauseClick)
                                    else
                                        onEvent(PlayerEvent.PlayClick)
                                }
                            )

                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(64.dp),
                                    color = colorScheme.onPrimary.copy(alpha = 0.5f),
                                    strokeWidth = 4.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(smallSpace))
                        NiceRoundButton(
                            contentDescription = "Fast Forward",
                            icon = Icons.Filled.FastForward,
                            diameter = 44.dp,
                            clickHandler = { onEvent(PlayerEvent.FastForward) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = largeSpace, start = largeSpace, end = largeSpace),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NiceRoundButton(
                            enabled = uiState.isSpeaking,
                            contentDescription = "Bookmark",
                            icon = Icons.Filled.BookmarkAdd,
                            diameter = 44.dp,
                            clickHandler = { onEvent(PlayerEvent.AddBookmark) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (uiState.chapters.isNotEmpty()) {
                            NiceRoundButton(
                                contentDescription = "Chapters",
                                icon = Icons.Filled.List,
                                diameter = 44.dp,
                                clickHandler = { showChaptersSheet = true }
                            )
                        } else {
                            Spacer(modifier = Modifier.width(44.dp))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        NiceRoundButton(
                            contentDescription = "Toggle Text Mode",
                            icon = Icons.Filled.TextFields,
                            diameter = 44.dp,
                            clickHandler = { onEvent(PlayerEvent.ToggleExtendedTextMode) }
                        )
                    }
                }
            }
        )

        if (showChaptersSheet) {
            ModalBottomSheet(
                onDismissRequest = { showChaptersSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    if (uiState.chapters.isNotEmpty()) {
                        Text(
                            "Chapters",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = if (uiState.isDyslexicFontEnabled) OpenDyslexic else FontFamily.Default
                            ),
                            modifier = Modifier.padding(16.dp)
                        )
                        HorizontalDivider()
                        LazyColumn {
                            items(uiState.chapters) { chapter ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            chapter.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = if (uiState.isDyslexicFontEnabled) OpenDyslexic else FontFamily.Default
                                            )
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        onEvent(PlayerEvent.ChapterClick(chapter))
                                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                                            if (!sheetState.isVisible) {
                                                showChaptersSheet = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No chapters available")
                        }
                    }
                }
            }
        }

        if (showSleepTimerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSleepTimerSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Sleep Timer",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = if (uiState.isDyslexicFontEnabled) OpenDyslexic else FontFamily.Default
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                    HorizontalDivider()
                    
                    val timerOptions = listOf(
                        "Off" to 0,
                        "5 Minutes" to 5,
                        "10 Minutes" to 10,
                        "15 Minutes" to 15,
                        "30 Minutes" to 30,
                        "45 Minutes" to 45,
                        "60 Minutes" to 60
                    )

                    LazyColumn {
                        items(timerOptions) { (label, minutes) ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = if (uiState.isDyslexicFontEnabled) OpenDyslexic else FontFamily.Default
                                        )
                                    )
                                },
                                modifier = Modifier.clickable {
                                    if (minutes == 0) {
                                        onEvent(PlayerEvent.CancelSleepTimer)
                                    } else {
                                        onEvent(PlayerEvent.SetSleepTimer(minutes))
                                    }
                                    showSleepTimerSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if ((uiState.isLoading || uiState.totalTimeString.isEmpty() || uiState.totalTimeString.endsWith("00:00")) && !uiState.isSpeaking) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xcF7f7f7f))
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(largeSpace)
                        .background(
                            color = colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = colorScheme.background,
                        modifier = Modifier.padding(largeSpace)
                    )
                    Text(
                        "Loading book..",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.background,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(largeSpace))
                }
            }
        }
    }

}

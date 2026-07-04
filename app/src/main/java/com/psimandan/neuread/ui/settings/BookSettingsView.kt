package com.psimandan.neuread.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.ui.text.style.TextOverflow
import com.psimandan.neuread.ui.theme.OpenDyslexic
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.psimandan.neuread.data.model.AudioBook
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.data.model.TextPart
import com.psimandan.neuread.ui.components.NiceButton
import com.psimandan.neuread.ui.components.NiceButtonLarge
import com.psimandan.neuread.ui.settings.components.ConfirmDeleteDialog
import com.psimandan.neuread.ui.settings.components.ErrorMessageDialog
import com.psimandan.neuread.ui.settings.components.HorizontalPageListView
import com.psimandan.neuread.ui.settings.components.LanguagePicker
import com.psimandan.neuread.ui.settings.components.SpeechSpeedSelector
import com.psimandan.neuread.ui.settings.components.VoicePicker
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.ui.theme.largeSpace
import com.psimandan.neuread.ui.theme.scTypography
import com.psimandan.neuread.ui.theme.smallSpace
import com.psimandan.neuread.ui.theme.normalSpace
import com.psimandan.neuread.voice.NeuReadVoice
import com.psimandan.neuread.voice.VoiceSelectorViewModel
import com.psimandan.neuread.voice.toLocale
import com.psimandan.neuread.voice.toVoice
import timber.log.Timber
import java.util.Locale

@Preview(showBackground = true)
@Composable
fun BookSettingsPreviewBook() {
    NeuReadTheme(darkTheme = false) {
        BookSettingsScreenContent(
            loading = false,
            downloadProgress = null,
            showVoiceError = false,
            bookState = BookSettingsViewModel.BookUIState(
                book = NeuReadBook.sampleBooks().last(),
                title = NeuReadBook.sampleBooks().last().title,
                author = NeuReadBook.sampleBooks().last().author
            ),
            contextText = listOf(
                "With this approach, you can now have selectable text in your view without allowing the user to modify the content. The text will be fully selectable, and users will be able to copy it to the clipboard by selecting and using the standard copy commands.",
                "Lorem ipsum2",
                "Lorem ipsum3"
            ),
            selectedPage = 0,
            selectedLanguage = Locale.getDefault(),
            availableLocales = listOf(),
            recentLocales = listOf(),
            availableVoices = listOf(),
            selectedVoice = NeuReadVoice("Voice 1", "en"),
            selectedRate = 1f,
            dyslexicFontEnabled = false,
            highlightingEnabled = true,
            isEditingText = false,
            editingText = "",
            onEvent = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BookSettingsPreviewAudiobook() {
    NeuReadTheme(darkTheme = true) {
        BookSettingsScreenContent(
            loading = false,
            downloadProgress = null,
            showVoiceError = false,
            bookState = BookSettingsViewModel.BookUIState(
                book = AudioBook(
                    id = "0",
                    title = "Moby Dick",
                    author = "Herman Melville",
                    language = "en_GB",
                    voiceRate = 1.25f,
                    parts = listOf(
                        TextPart(0, "Call me Ishmael."),
                        TextPart(1, "Call me Ishmael.")
                    ),
                    lastPosition = 0,
                    updated = System.currentTimeMillis(),
                    audioFilePath = "",
                    voice = "Kokoko",
                    model = "Male, George",
                    bookSource = "www.gutenberg.org"
                ),
                title = NeuReadBook.sampleBooks().first().title,
                author = NeuReadBook.sampleBooks().first().author
            ),
            contextText = listOf(
                "With this approach, you can now have selectable text in your view without allowing the user to modify the content. The text will be fully selectable, and users will be able to copy it to the clipboard by selecting and using the standard copy commands.",
                "Lorem ipsum2",
                "Lorem ipsum3"
            ),
            selectedPage = 0,
            selectedLanguage = Locale.getDefault(),
            availableLocales = listOf(),
            recentLocales = listOf(),
            availableVoices = listOf(),
            selectedVoice = NeuReadVoice("Voice 1", "en"),
            selectedRate = 1f,
            dyslexicFontEnabled = false,
            highlightingEnabled = true,
            isEditingText = false,
            editingText = "",
            onEvent = {}
        )
    }
}

@Composable
fun BookSettingsScreenView(
    onBookDeleted: () -> Unit,
    onNavigateBack: (NeuReadBook?) -> Unit,
    viewModel: BookSettingsViewModel,
    voiceSelector: VoiceSelectorViewModel
) {
    val voices by voiceSelector.availableVoices.collectAsState()
    val locales by voiceSelector.availableLocales.collectAsState()
    val recentLocales = viewModel.recentSelectionsL.values

    val bookState by viewModel.bookState.collectAsState()
    val viewState by viewModel.viewState.collectAsState()
    val showVoiceError = viewState.showVoiceError
    val contextText = bookState.text.ifEmpty {
        bookState.parts.map { it.text }
    }
    val selectedLanguage = bookState.language.toLocale()
    val selectedVoice = voiceSelector.nameToVoice(
        bookState.voiceIdentifier.ifBlank {
            (bookState.book as? AudioBook)?.voice.orEmpty()
        },
        bookState.language
    )
//    val selectedRate = bookState.voiceRate


    LaunchedEffect(Unit) {
        viewModel.setUpBook()
        viewModel.loadSettings()
        voiceSelector.loadVoices()
    }

    BookSettingsScreenContent(
        loading = viewState.loading,
        downloadProgress = viewState.downloadProgress,
        showVoiceError = showVoiceError,
        bookState = bookState,
        contextText = contextText,
        selectedPage = viewState.selectedPage,
        selectedLanguage = selectedLanguage,
        recentLocales = recentLocales.toList(),
        availableLocales = locales.toList(),
        availableVoices = voices.toList(),
        selectedRate = bookState.voiceRate,
        selectedVoice = selectedVoice,
        dyslexicFontEnabled = viewState.dyslexicFontEnabled,
        highlightingEnabled = viewState.highlightingEnabled,
        isEditingText = viewState.isEditingText,
        editingText = viewState.editingText,
        onEvent = { it.onEvent(model = viewModel, onNavigateBack = onNavigateBack) }
    )

    androidx.activity.compose.BackHandler {
        BookSettingsEvent.Cancel.onEvent(model = viewModel, onNavigateBack = onNavigateBack)
    }

    if (viewState.showDeleteDialog) {
        ConfirmDeleteDialog(
            onDeleteClicked = {
                viewModel.onDelete(onBookDeleted)
            },
            onDismissRequest = {
                viewModel.onShowDelete(false)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSettingsScreenContent(
    loading: Boolean,
    downloadProgress: Float? = null,
    showVoiceError: Boolean,
    bookState: BookSettingsViewModel.BookUIState,
    contextText: List<String>,
    selectedPage: Int,
    recentLocales: List<String>,
    availableLocales: List<Locale>,
    availableVoices: List<NeuReadVoice>,
    selectedVoice: NeuReadVoice,
    selectedRate: Float,
    selectedLanguage: Locale,
    dyslexicFontEnabled: Boolean,
    highlightingEnabled: Boolean,
    isEditingText: Boolean,
    editingText: String,
    onEvent: (BookSettingsEvent) -> Unit
) {
    Box(Modifier.background(colorScheme.background)) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Book Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = { onEvent(BookSettingsEvent.Cancel) }) {
                            Text("Cancel", color = colorScheme.primary)
                        }
                    },
                    actions = {
                        TextButton(onClick = { onEvent(BookSettingsEvent.Save) }) {
                            Text("Save", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            },
            content = { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .padding(largeSpace)
                        .verticalScroll(rememberScrollState())
                ) {
                    val showLanguageDialog = remember { mutableStateOf(false) }
                    val showVoiceDialog = remember { mutableStateOf(false) }

                    BookTitleSection(
                        title = bookState.title,
                        author = bookState.author,
                        onTitleChanged = { onEvent(BookSettingsEvent.TitleChanged(it)) },
                        onAuthorChanged = { onEvent(BookSettingsEvent.AuthorChanged(it)) }
                    )
                    Spacer(Modifier.height(normalSpace))
                    HorizontalDivider(color = colorScheme.outlineVariant)
                    Spacer(Modifier.height(normalSpace))
                    Column(
                        modifier = Modifier
                            .padding()
                    ) {
                        if (bookState.book is Book) {
                            Text(
                                text = "Selected Language",
                                style = scTypography.titleMedium,
                                color = colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(smallSpace))
                            NiceButton(
                                title = selectedLanguage.displayName,
                                clickHandler = {
                                    showLanguageDialog.value = true
                                },
                            )
                        } else if (bookState.book is AudioBook) {
                            val book = bookState.book
                            Text(
                                text = "Book's Language",
                                style = scTypography.titleMedium,
                                color = colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(smallSpace))
                            val locale = book.language.toLocale()
                            Text(
                                text = "${locale.displayLanguage} (${locale.displayCountry})",
                                style = scTypography.titleLarge,
                                color = colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = smallSpace)
                            )
                        }

                        Spacer(Modifier.height(normalSpace))
                        SpeechSpeedSelector(
                            defaultSpeed = selectedRate,
                            onSpeedSelected = { onEvent(BookSettingsEvent.SpeedSelected(it)) })
                        Spacer(Modifier.height(normalSpace))
                        Text(
                            text = "Selected Voice",
                            style = scTypography.titleMedium,
                            color = colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(smallSpace))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                        ) {
                            val audioBook = bookState.book as? AudioBook

                            if (bookState.book is Book || audioBook != null) {
                                NiceButton(
                                    title = selectedVoice.name,
                                    clickHandler = { showVoiceDialog.value = true },
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            IconButton(onClick = {
                                onEvent(
                                    BookSettingsEvent.PlayVoiceSample(
                                        selectedLanguage,
                                        selectedVoice.toVoice(),
                                        selectedRate
                                    )
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleOutline,
                                    contentDescription = "play voice sample",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            if (bookState.book is AudioBook) {
                                IconButton(onClick = {
                                    onEvent(BookSettingsEvent.PlayAudioSample)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = "play book audio",
                                        tint = colorScheme.secondary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            if (downloadProgress != null) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    IconButton(onClick = { onEvent(BookSettingsEvent.CancelDownload) }) {
                                        Box(
                                            modifier = Modifier.size(44.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val animatedProgress by animateFloatAsState(
                                                targetValue = downloadProgress,
                                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                label = "downloadProgress"
                                            )
                                            CircularProgressIndicator(
                                                progress = { animatedProgress },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(4.dp),
                                                color = colorScheme.primary,
                                                strokeWidth = 3.dp,
                                                trackColor = colorScheme.surfaceVariant
                                            )
                                            Text(
                                                text = "${(animatedProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }
                                    }
                                }
                            }

                            when (val currentBook = bookState.book) {
                                is Book -> {
                                    if (downloadProgress == null && selectedVoice.requiresNetworkConnection) {
                                        IconButton(onClick = { onEvent(BookSettingsEvent.DownloadAudio) }) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "download audio",
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                    }
                                }

                                is AudioBook -> {
                                    if (downloadProgress == null && (
                                            selectedVoice.name != currentBook.voice ||
                                                    selectedRate != currentBook.voiceRate
                                            )) {
                                        IconButton(onClick = { onEvent(BookSettingsEvent.DownloadAudio) }) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "redownload audio",
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                    }

                                    IconButton(onClick = { onEvent(BookSettingsEvent.DeleteAudio) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "delete audio",
                                            tint = colorScheme.error,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }

                                null -> Unit
                            }

                        }
                        Spacer(Modifier.height(normalSpace))
                        if (bookState.book is Book) {
                            Text(
                                text = "Select number of Pages to skip",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        } else {
                            Text(
                                text = "Text Preview",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        HorizontalPageListView(
                            selectedPage = selectedPage,
                            totalPages = contextText.size,
                            onPageChanged = { onEvent(BookSettingsEvent.PageSelected(it)) }
                        )

                        Spacer(Modifier.height(normalSpace))

                        if (contextText.isNotEmpty()) {
                            Text(
                                text = "Font Preview",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(normalSpace)
                            ) {
                                val previewText = contextText.getOrNull(selectedPage) ?: ""
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = if (dyslexicFontEnabled) OpenDyslexic else FontFamily.Default
                                    ),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(normalSpace))
                        HorizontalDivider(color = colorScheme.outlineVariant)
                        Spacer(Modifier.height(normalSpace))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEvent(BookSettingsEvent.ToggleDyslexicFont(!dyslexicFontEnabled)) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dyslexia Friendly Font",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "Use OpenDyslexic font for book text",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dyslexicFontEnabled,
                                onCheckedChange = { onEvent(BookSettingsEvent.ToggleDyslexicFont(it)) }
                            )
                        }

                        Spacer(Modifier.height(normalSpace))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEvent(BookSettingsEvent.ToggleHighlighting(!highlightingEnabled)) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Word Highlighting",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "Highlight words during playback",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = highlightingEnabled,
                                onCheckedChange = { onEvent(BookSettingsEvent.ToggleHighlighting(it)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(normalSpace))
                    NiceButtonLarge(
                        title = if (bookState.bookSource == "clipboard") "Edit Entire Text" else "Edit Current Page Content",
                        color = colorScheme.secondary,
                        clickHandler = { onEvent(BookSettingsEvent.OpenTextEditor) },
                    )
                    Spacer(Modifier.height(normalSpace))
                    NiceButtonLarge(title = "Delete This Book", color = colorScheme.error) {
                        onEvent(BookSettingsEvent.DeleteClicked)
                    }
                    if (showLanguageDialog.value) {
                        LanguagePicker(defaultLanguage = selectedLanguage,
                            availableLocales = availableLocales,
                            recentLocales = recentLocales,
                            onLanguageSelected = {
                                onEvent(BookSettingsEvent.LanguageSelected(it))
                                Timber.d("onLanguageSelected=>$it")
                                showLanguageDialog.value = false
                            }, onDismiss = {
                                showLanguageDialog.value = false
                            })
                    } else if (showVoiceDialog.value) {
                        VoicePicker(selectedLanguage = selectedLanguage,
                            defaultVoice = selectedVoice,
                            availableVoices = availableVoices,
                            onPlaySample = {
                                onEvent(
                                    BookSettingsEvent.PlayVoiceSample(
                                        selectedLanguage,
                                        it.toVoice(),
                                        selectedRate
                                    )
                                )
                            },
                            onSave = {
                                onEvent(BookSettingsEvent.VoiceSelected(it))
                                showVoiceDialog.value = false
                            },
                            onDismiss = {
                                showVoiceDialog.value = false
                            })
                    }
                }
            }
        )
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        if (showVoiceError) {
            ErrorMessageDialog(
                title = "Voice Error",
                message = "Something went wrong. Please check your internet connection and try again. Go to Settings and check if Text-to-Speech and voices are available on your phone.",
                onDismissRequest = {
                    onEvent(BookSettingsEvent.DismissVoiceErrorDialog)
                }
            )
        }
    }

    if (isEditingText) {
        TextEditorDialog(
            initialText = editingText,
            onSave = { onEvent(BookSettingsEvent.SaveEditedText(it)) },
            onDismiss = { onEvent(BookSettingsEvent.CloseTextEditor) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorDialog(
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(largeSpace)
                    .fillMaxSize()
            ) {
                Text(
                    text = "Edit Page Content",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = normalSpace)
                )
                
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline
                    )
                )
                
                Spacer(modifier = Modifier.height(normalSpace))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(normalSpace))
                    Button(onClick = { onSave(text) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookTitleSection(
    title: String,
    author: String,
    onTitleChanged: (String) -> Unit,
    onAuthorChanged: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    focusManager.moveFocus(FocusDirection.Enter)

    Column {
        OutlinedTextField(
            value = title,
            placeholder = {
                Text(
                    text = "Input title",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            ),
            onValueChange = onTitleChanged,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Text
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (title.isNotEmpty()) {
                    IconButton(onClick = { onTitleChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear text",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
        Spacer(Modifier.height(normalSpace))
        OutlinedTextField(
            value = author,
            placeholder = {
                Text(
                    text = "Input author",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = colorScheme.onSurface
            ),
            onValueChange = onAuthorChanged,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Text
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (author.isNotEmpty()) {
                    IconButton(onClick = { onAuthorChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear text",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}
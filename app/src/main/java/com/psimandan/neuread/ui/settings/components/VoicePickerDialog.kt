package com.psimandan.neuread.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.ui.theme.largeSpace
import com.psimandan.neuread.voice.NeuReadVoice
import com.psimandan.neuread.voice.languageId
import java.util.Locale

@Preview(showBackground = true)
@Composable
fun VoicePickerPreview() {
    NeuReadTheme {
        VoicePicker(
            selectedLanguage = Locale.ENGLISH,
            availableVoices = listOf(
                NeuReadVoice("Voice 1", "en"),
                NeuReadVoice("Voice 2", "en"),
                NeuReadVoice("Voice 3", "en"),
                NeuReadVoice("Voice 4", "en")

            ),
            defaultVoice = NeuReadVoice("Voice 2", "en"),
            onVoiceSelected = {},
            onSave = {},
            onDeleteVoice = {},
            onDismiss = {}
        )
    }
}


@Composable
fun VoicePicker(
    selectedLanguage: Locale,
    availableVoices: List<NeuReadVoice>,
    defaultVoice: NeuReadVoice,
    onVoiceSelected: (NeuReadVoice) -> Unit,
    onSave: (NeuReadVoice) -> Unit,
    onDeleteVoice: (NeuReadVoice) -> Unit,
    onDismiss: () -> Unit
) {
    val supportedVoices = remember(availableVoices, selectedLanguage) {
        availableVoices.filter {
            it.locale.languageId() == selectedLanguage.languageId()
        }
    }

    val selectedVoice = remember {
        mutableStateOf(defaultVoice)
    }

    fun isSelected(voice: NeuReadVoice): Boolean {
        return voice.name == selectedVoice.value.name
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            Button(onClick = { onSave(selectedVoice.value) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = { onDismiss() }) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = "Select Voice",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                LazyColumn {
                    items(supportedVoices.size) { index ->
                        val voice = supportedVoices[index]
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected(voice)) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = (isSelected(voice))) {
                                    selectedVoice.value = supportedVoices[index]
                                    onVoiceSelected(selectedVoice.value)
                                },
                            headlineContent = {
                                Text(
                                    voice.name,
                                    color = if (isSelected(voice)) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (voice.clonedVoice != null) {
                                        IconButton(onClick = {
                                            onDeleteVoice(voice)
                                            if (isSelected(voice)) {
                                                selectedVoice.value = supportedVoices.firstOrNull { it.name != voice.name } ?: defaultVoice
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Voice",
                                                tint = if (isSelected(voice)) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        onVoiceSelected(voice)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircleOutline,
                                            contentDescription = "Play Sample",
                                            tint = if (isSelected(voice)) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        modifier = Modifier.padding(vertical = largeSpace)
    )
}

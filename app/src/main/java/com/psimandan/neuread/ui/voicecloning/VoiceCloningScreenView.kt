package com.psimandan.neuread.ui.voicecloning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.psimandan.neuread.voice.NeuReadVoice
import com.psimandan.neuread.voice.VoiceSelectorViewModel
import com.psimandan.neuread.voice.languageId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCloningScreenView(
    viewModel: VoiceSelectorViewModel,
    onNavigateBack: () -> Unit,
    onAddVoiceClicked: () -> Unit
) {
    val clonedVoices by viewModel.clonedVoices.collectAsState()
    val availableLocales by viewModel.availableLocales.collectAsState()
    
    var voiceToDelete by remember { mutableStateOf<NeuReadVoice?>(null) }
    var voiceToEdit by remember { mutableStateOf<NeuReadVoice?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadVoices()
    }

    if (voiceToDelete != null) {
        AlertDialog(
            onDismissRequest = { voiceToDelete = null },
            title = { Text("Delete Voice") },
            text = { Text("Are you sure you want to delete '${voiceToDelete?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        voiceToDelete?.let { viewModel.deleteVoice(it) }
                        voiceToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { voiceToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (voiceToEdit != null) {
        var name by remember { mutableStateOf(voiceToEdit?.name ?: "") }
        var selectedLanguage by remember { mutableStateOf(voiceToEdit?.language ?: "en_US") }
        var expanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { voiceToEdit = null },
            title = { Text("Edit Voice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Voice Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box {
                        OutlinedTextField(
                            value = selectedLanguage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Language") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            availableLocales.forEach { locale ->
                                DropdownMenuItem(
                                    text = { Text(locale.displayName) },
                                    onClick = {
                                        selectedLanguage = locale.languageId()
                                        expanded = false
                                    }
                                )
                            }
                        }
                        // Workaround to make the whole field clickable
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expanded = true }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        voiceToEdit?.let { viewModel.updateVoice(it, name, selectedLanguage) }
                        voiceToEdit = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { voiceToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Cloning") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAddVoiceClicked) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Voice"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (clonedVoices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No voices cloned yet")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(clonedVoices) { voice ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text(voice.name) },
                            supportingContent = { Text(voice.language) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { voiceToEdit = voice }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Voice"
                                        )
                                    }
                                    IconButton(onClick = { voiceToDelete = voice }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Voice",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

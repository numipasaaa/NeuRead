package com.psimandan.neuread.ui.voicecloning

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.psimandan.neuread.voice.VoiceSelectorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCloningRecordingScreenView(
    viewModel: VoiceCloningViewModel,
    voiceSelectorViewModel: VoiceSelectorViewModel,
    onNavigateBack: () -> Unit
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingCompleted by viewModel.recordingCompleted.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadSuccess by viewModel.uploadSuccess.collectAsState()

    val context = LocalContext.current
    val prompts = mapOf(
        "en" to "So I'm live on radio. And I say, well, my dear friend James here clearly, and the whole room just froze. Turns out I'd completely misspoken and mentioned our other friend.",
        "ro" to "Deci sunt în direct la radio. Și spun, ei bine, dragul meu prieten Ion este aici, și toată sala a înghețat. Se pare că am vorbit greșit și l-am menționat pe celălalt prieten al nostru.",
        "es" to "Además su eficiencia depende del clima. En días nublados o durante la noche producen menos energía.",
        "de" to "Es wurde eine Untersuchung zur Aufklärung des Unfalls eingeleitet.",
        "fr" to "Dans les zones rurales où de nombreuses communautés n'ont pas accès à l'électricité, l'énergie solaire peut fare une énorme différence."
    )

    var voiceName by remember { mutableStateOf("") }
    var selectedLanguageCode by remember { mutableStateOf("en") }
    var expanded by remember { mutableStateOf(false) }

    val prompt = prompts[selectedLanguageCode] ?: prompts["en"]!!

    val allowedLanguageCodes = listOf("en", "ro", "es", "de", "fr")

    LaunchedEffect(Unit) {
        viewModel.resetRecording()
        voiceSelectorViewModel.loadVoices()
    }

    LaunchedEffect(uploadSuccess) {
        if (uploadSuccess == true) {
            onNavigateBack()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.startRecording()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Voice") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isUploading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!recordingCompleted) {
                        Text(
                            text = "Please read the following text clearly:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = prompt,
                                modifier = Modifier.padding(24.dp),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    lineHeight = 32.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Symmetry spacer on the left to keep record button centered
                            Spacer(modifier = Modifier.width(88.dp))

                            LargeFloatingActionButton(
                                onClick = {
                                    if (isRecording) {
                                        viewModel.stopRecording()
                                    } else {
                                        when (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        )) {
                                            PackageManager.PERMISSION_GRANTED -> {
                                                viewModel.startRecording()
                                            }

                                            else -> {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    }
                                },
                                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            Box {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.size(64.dp),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                    enabled = !isRecording,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = selectedLanguageCode.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    allowedLanguageCodes.forEach { code ->
                                        DropdownMenuItem(
                                            text = {
                                                val label = when (code) {
                                                    "en" -> "English"
                                                    "ro" -> "Romanian"
                                                    "es" -> "Spanish"
                                                    "de" -> "German"
                                                    "fr" -> "French"
                                                    else -> code
                                                }
                                                Text("$label (${code.uppercase()})")
                                            },
                                            onClick = {
                                                selectedLanguageCode = code
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isRecording) "Recording..." else "Tap to start recording",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Recording completed!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        val isPlaying by viewModel.isPlaying.collectAsState()

                        Button(
                            onClick = {
                                if (isPlaying) viewModel.stopPlayback() else viewModel.playRecording()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlaying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isPlaying) "Stop Preview" else "Play Recording")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = voiceName,
                            onValueChange = { voiceName = it },
                            label = { Text("Voice Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (voiceName.isNotBlank()) {
                                    val languageId = when (selectedLanguageCode) {
                                        "en" -> "en_US"
                                        "ro" -> "ro_RO"
                                        "es" -> "es_ES"
                                        "de" -> "de_DE"
                                        "fr" -> "fr_FR"
                                        else -> "en_US"
                                    }
                                    viewModel.uploadVoice(voiceName, languageId, prompt)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = voiceName.isNotBlank()
                        ) {
                            Text("Finish and Upload")
                        }

                        if (uploadSuccess == false) {
                            Text(
                                text = "Upload failed. Please try again.",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        TextButton(
                            onClick = { viewModel.resetRecording() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Record Again")
                        }
                    }
                }
            }
        }
    }
}

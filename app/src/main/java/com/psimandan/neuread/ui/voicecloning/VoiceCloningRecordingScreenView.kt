package com.psimandan.neuread.ui.voicecloning

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.psimandan.neuread.ui.components.LoadingStateManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCloningRecordingScreenView(
    viewModel: VoiceCloningViewModel,
    onNavigateBack: () -> Unit
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingCompleted by viewModel.recordingCompleted.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadSuccess by viewModel.uploadSuccess.collectAsState()
    
    val context = LocalContext.current
    val prompt = "The quick brown fox jumps over the lazy dog. Recording your voice helps us create a unique digital double for you."

    var voiceName by remember { mutableStateOf("") }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                        containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isRecording) "Recording..." else "Tap to start recording",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
                                viewModel.uploadVoice(voiceName, prompt)
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
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Record Again")
                    }
                }
            }
        }
    }
}

package com.psimandan.neuread.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.psimandan.neuread.ui.components.NiceButtonLarge
import com.psimandan.neuread.ui.theme.largeSpace
import com.psimandan.neuread.ui.theme.normalSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenView(
    onNavigateBack: () -> Unit,
    onAboutClicked: () -> Unit,
    onVoiceCloningClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(largeSpace)
            ) {
                Text(
                    text = "General Settings",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(normalSpace))
                NiceButtonLarge(
                    title = "Voice Cloning",
                    color = MaterialTheme.colorScheme.primary
                ) {
                    onVoiceCloningClicked()
                }
                Spacer(modifier = Modifier.height(normalSpace))
                NiceButtonLarge(
                    title = "About NeuRead",
                    color = MaterialTheme.colorScheme.primary
                ) {
                    onAboutClicked()
                }
            }
        }
    )
}

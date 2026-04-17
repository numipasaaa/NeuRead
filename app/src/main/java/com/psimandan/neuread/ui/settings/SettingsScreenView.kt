package com.psimandan.neuread.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.psimandan.neuread.ui.components.NiceButtonLarge
import com.psimandan.neuread.ui.theme.largeSpace
import com.psimandan.neuread.ui.theme.normalSpace
import com.psimandan.neuread.ui.theme.smallSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenView(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onAboutClicked: () -> Unit,
    onVoiceCloningClicked: () -> Unit
) {
    val currentAccentColorInt by viewModel.accentColor.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val currentAccentColor = currentAccentColorInt?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    val colors = listOf(
        Color(0xFF2196F3), // Original Blue
        Color(0xFFE91E63), // Pink
        Color(0xFF9C27B0), // Purple
        Color(0xFF673AB7), // Deep Purple
        Color(0xFF3F51B5), // Indigo
        Color(0xFF009688), // Teal
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFFFF5722), // Deep Orange
    )

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
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(normalSpace))
                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(smallSpace))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { color ->
                        val isSelected = currentAccentColorInt != null && color.toArgb() == currentAccentColorInt
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { viewModel.updateAccentColor(color) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(largeSpace))
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(smallSpace))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("Auto", "Light", "Dark")
                    modes.forEachIndexed { index, mode ->
                        FilterChip(
                            selected = themeMode == index,
                            onClick = { viewModel.updateThemeMode(index) },
                            label = { Text(mode) },
                            leadingIcon = if (themeMode == index) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(largeSpace))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(largeSpace))

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

package com.psimandan.neuread.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.psimandan.neuread.ui.theme.NeuReadTheme
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SpeechSpeedSelector(
    defaultSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var selectedSpeed by remember { mutableFloatStateOf(defaultSpeed) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Ensure selectedSpeed updates when defaultSpeed changes
    LaunchedEffect(defaultSpeed) {
        selectedSpeed = defaultSpeed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Speech Rate",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(speeds) { speed ->
                val isSelected = selectedSpeed == speed
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            selectedSpeed = speed
                            onSpeedSelected(speed)
                            coroutineScope.launch {
                                val index = speeds.indexOf(speed)
                                if (index != -1) {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.2f", speed),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    // Scroll to the default speed when the view first appears
    LaunchedEffect(defaultSpeed) {
        val index = speeds.indexOf(defaultSpeed)
        if (index != -1) {
            listState.scrollToItem(index) // Use `scrollToItem` instead of animateScroll for initial setup
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpeechSpeedSelectorPreview() {
    NeuReadTheme(darkTheme = false) {
        SpeechSpeedSelector(defaultSpeed = 1.0f) { newSpeed ->
            println("Selected speed: $newSpeed")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpeechSpeedSelectorPreviewDark() {
    NeuReadTheme(darkTheme = true) {
        SpeechSpeedSelector(defaultSpeed = 1.0f) { newSpeed ->
            println("Selected speed: $newSpeed")
        }
    }
}
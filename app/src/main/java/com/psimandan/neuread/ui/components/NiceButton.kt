package com.psimandan.neuread.ui.components


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.ui.theme.smallSpace


@Preview
@Composable
fun TestNiceButtonSmall() {
    NeuReadTheme {
        val loading = LoadingStateManager().apply {
            Remember()
        }
        Column {
            NiceButton(
                loading = loading.state(),
                title = "Start",
                modifier = Modifier.fillMaxWidth(),
                clickHandler = {
                    loading.onLoadingBegins()
                }
            )
            Spacer(modifier = Modifier.height(smallSpace))
            NiceButton(
                enabled = false,
                title = "Starting",
                modifier = Modifier.fillMaxWidth(),
                clickHandler = {
                    loading.onLoadingBegins()
                }
            )
            Spacer(modifier = Modifier.height(smallSpace))
            NiceButton(
                title = "End",
                color = colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                clickHandler = {
                    loading.onLoadingEnds()
                }
            )
            Spacer(modifier = Modifier.height(smallSpace))
            Row {
                NiceRoundButton(
                    contentDescription = "Merge And Record",
                    icon = Icons.Filled.CallMerge,
                    clickHandler = {
                        loading.onLoadingBegins()
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                NiceRoundButton(
                    enabled = false,
                    contentDescription = "Merge And Record",
                    icon = Icons.Filled.CallMerge,
                    clickHandler = {
                        loading.onLoadingBegins()
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                NiceRoundButton(
                    contentDescription = "Merge And Record",
                    icon = Icons.Filled.CallMerge,
                    clickHandler = {
                    }
                )
            }
        }
    }
}

@Composable
fun NiceButtonSmall(title: String, clickHandler: () -> Unit) {
    NiceButton(
        title = title,
        modifier = Modifier.width(100.dp),
        clickHandler = clickHandler
    )
}

@Composable
fun NiceButtonMedium(
    enabled: Boolean = true,
    title: String,
    clickHandler: () -> Unit = {}
) {
    NiceButton(
        enabled = enabled,
        title = title,
        modifier = Modifier.width(150.dp),
        clickHandler = clickHandler
    )
}

@Composable
fun NiceButtonLarge(
    title: String,
    color: Color,
    clickHandler: () -> Unit
) {
    val loading = LoadingStateManager().apply {
        Remember()
    }
    NiceButton(
        loading = loading.state(),
        title = title,
        modifier = Modifier.fillMaxWidth(),
        color = color,
        clickHandler = clickHandler
    )
}

@Composable
fun NiceRoundButton(
    enabled: Boolean = true,
    contentDescription: String,
    icon: ImageVector,
    diameter: Dp = 44.dp,
    scale: Float = 1f,
    backgroundColor: Color = colorScheme.primary,
    tint: Color = colorScheme.surface,
    clickHandler: () -> Unit = {}
) {
    Box(contentAlignment = Alignment.Center) {
        FloatingActionButton(
            containerColor = if (enabled) backgroundColor else Color.Gray,
            modifier = Modifier.width(diameter).height(diameter),
            onClick = {
                if (enabled) {
                    clickHandler()
                }
            }) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.scale(scale)
            )
        }
    }
}

@Composable
fun NiceButton(
    enabled: Boolean = true,
    loading: LoadingState = LoadingState.Hide,
    title: String,
    titleColor: Color = colorScheme.onPrimary,
    modifier: Modifier = Modifier,
    color: Color = colorScheme.primary,
    clickHandler: () -> Unit = {}
) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) color else colorScheme.onSurface.copy(alpha = 0.12f),
        animationSpec = tween(300),
        label = "containerColor"
    )

    OutlinedButton(
        enabled = enabled,
        onClick = {
            if (loading != LoadingState.Show) {
                clickHandler()
            }
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        modifier = modifier,
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.5f) else Color.Transparent),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colorScheme.onPrimary,
            containerColor = containerColor
        ),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading == LoadingState.Show) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = title,
                    style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.38f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

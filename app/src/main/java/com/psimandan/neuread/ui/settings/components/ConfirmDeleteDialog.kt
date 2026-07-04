package com.psimandan.neuread.ui.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psimandan.neuread.ui.components.NiceButton
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.ui.theme.*
import java.util.*

@Preview(showBackground = true)
@Composable
fun ConfirmDeleteDialogLightThemePreview() {
    NeuReadTheme(darkTheme = false) {
        ConfirmDeleteDialog(
            onDeleteClicked = {},
            onDismissRequest = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConfirmDeleteDialogDarkThemePreview() {
    NeuReadTheme(darkTheme = true) {
        ConfirmDeleteDialog(
            onDeleteClicked = {},
            onDismissRequest = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeleteDialogLightThemePreview() {
    NeuReadTheme(darkTheme = false) {
        DeleteDialog(
            onDeleteClicked = {},
            onDismissRequest = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeleteDialogDarkThemePreview() {
    NeuReadTheme(darkTheme = true) {
        DeleteDialog(
            onDeleteClicked = {},
            onDismissRequest = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ErrorMessageDialogLightThemePreview() {
    NeuReadTheme(darkTheme = false) {
        ErrorMessageDialog(
            message = "Something went wrong. Please check your internet connection and try again.",
            onDismissRequest = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ErrorMessageDialogDarkThemePreview() {
    NeuReadTheme(darkTheme = true) {
        ErrorMessageDialog(
            message = "Something went wrong. Please check your internet connection and try again.",
            onDismissRequest = {}
        )
    }
}

@Composable
fun ConfirmDeleteDialog(
    onDeleteClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { },
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Are you sure\nyou want to\ndelete this\nbook?",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.W400,
                        color = colorScheme.onSurface
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "You cannot undo this action!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W300,
                        color = colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDeleteClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(percent = 50),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = "DELETE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.W600,
                            fontSize = 16.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(percent = 50),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.W500,
                            fontSize = 16.sp
                        )
                    )
                }
            }
        },
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(32.dp)
    )
}

@Composable
fun DeleteDialog(
    onDeleteClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { onDeleteClicked() },
        dismissButton = { onDismissRequest() },
        title = {
            Text(
                text = "Delete Conversation",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = @Composable {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .height(200.dp)
                    .padding(
                        largeSpace
                    )
            ) {
                Spacer(modifier = Modifier.height(normalSpace))
                Text(
                    text = "Are you sure? You cannot undo this action!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Row {
                    NiceButton(
                        title = "DELETE",
                        titleColor = MaterialTheme.colorScheme.onError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(120.dp),
                        clickHandler = onDeleteClicked
                    )
                    Spacer(Modifier.weight(1f))
                    NiceButton(
                        title = "Cancel",
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                        clickHandler = onDismissRequest
                    )
                }
                Spacer(modifier = Modifier.height(smallSpace))
            }
        }
    )
}


@Composable
fun ErrorMessageDialog(
    title: String = "Error",
    message: String = "Something went wrong. Please check your internet connection and try again.",
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        dismissButton = {
            NiceButton(
                title = "Cancel",
                color = MaterialTheme.colorScheme.primary,
                titleColor = MaterialTheme.colorScheme.surface,
                clickHandler = onDismissRequest
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = @Composable {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}


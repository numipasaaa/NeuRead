package com.psimandan.neuread.ui.player.components

import android.text.TextUtils
import android.view.View.LAYOUT_DIRECTION_RTL
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.ui.theme.OpenDyslexic
import java.util.Locale


@Preview(showBackground = true)
@Composable
fun HorizontallyScrolledTextViewPreviewDark() {
    NeuReadTheme(darkTheme = true) {
        HorizontallyScrolledTextView(
            highLight=true,
            words = listOf("Hello", "Compose", "Preview", "How", "Are", "You", "Doing", "Today"),
            index = 1,
            Locale.getDefault()
        )
    }

}

@Preview(showBackground = true)
@Composable
fun HorizontallyScrolledTextViewPreview() {
    NeuReadTheme(darkTheme = false) {
        HorizontallyScrolledTextView(
            highLight=false,
            words = listOf("Hello", "Compose", "Preview", "How", "Are", "You", "Doing", "Today"),
            index = 1,
            Locale.getDefault()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HorizontallyScrolledTextViewPreviewRTL() {
    NeuReadTheme(darkTheme = false) {
        HorizontallyScrolledTextView(
            highLight=true,
            words = listOf("שלום", "כתוב", "תצוגה מקדימה", "איך", "אתה", "עושה", "היום"),
            index = 4,
            Locale("iw", "IL")
        )
    }
}

@Composable
fun HorizontallyScrolledTextView(
    highLight: Boolean = true,
    words: List<String>, index: Int, language: Locale,
    isDyslexicFontEnabled: Boolean = false
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isRTL = TextUtils.getLayoutDirectionFromLocale(language) == LAYOUT_DIRECTION_RTL
    val layoutDirection = if (isRTL) LayoutDirection.Rtl else LayoutDirection.Ltr

    LaunchedEffect(index) {
        coroutineScope.launch {
            listState.animateScrollToItem(
                (index - 3).coerceAtLeast(0),
                scrollOffset = (index + 3).coerceAtMost(words.size - 1)
            )
        }
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.background(colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .fillMaxWidth()
            .height(56.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(words) { i, word ->
                    val isSelected = highLight && (i == index)
                    val backgroundColor = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val textColor = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        BasicText(
                            text = word,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                fontFamily = if (isDyslexicFontEnabled) OpenDyslexic else FontFamily.Default
                            )
                        )
                    }
                }
            }
        }
    }
}
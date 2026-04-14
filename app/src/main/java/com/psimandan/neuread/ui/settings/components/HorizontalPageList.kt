package com.psimandan.neuread.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psimandan.neuread.ui.theme.NeuReadTheme


@Composable
fun HorizontalPageListView(
    selectedPage: Int,
    totalPages: Int,
    onPageChanged: (Int) -> Unit
) {
    var currentPage by remember { mutableIntStateOf(selectedPage) }
    val listState = rememberLazyListState()

    // Scroll to selectedPage when first loaded or when selection changes
    LaunchedEffect(currentPage) {
        listState.animateScrollToItem(currentPage)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(totalPages) { pageIndex ->
            Box(
                modifier = Modifier
                    .background(
                        color = if (currentPage == pageIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        currentPage = pageIndex
                        onPageChanged(pageIndex)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (currentPage == pageIndex) MaterialTheme.colorScheme.surface else Color.Black,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHorizontalPageListView() {
    NeuReadTheme(darkTheme = false) {
        HorizontalPageListView(
            selectedPage = 3,
            totalPages = 15,
            onPageChanged = {}
        )
    }
}

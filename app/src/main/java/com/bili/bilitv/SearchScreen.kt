package com.bili.bilitv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onSearch: (String) -> Unit = {}
) {
    val searchText = viewModel.searchInput
    val focusRequester = remember { FocusRequester() }
    val hotSearches = viewModel.hotSearches
    val isLoadingHot = viewModel.isLoadingHotSearches
    val hotSearchError = viewModel.hotSearchError
    val suggestions = viewModel.searchSuggestions
    val suggestError = viewModel.suggestError
    val lastFocusArea = viewModel.lastFocusArea
    val lastFocusIndex = viewModel.lastFocusIndex

    LaunchedEffect(Unit) { viewModel.loadHotSearches() }
    LaunchedEffect(searchText) { viewModel.requestSuggestions(searchText) }
    LaunchedEffect(lastFocusArea, suggestions.size, hotSearches.size, viewModel.searchHistory.size) {
        when (lastFocusArea) {
            "suggest" -> {
                // 实际请求在每个item内处理
            }
            "hot" -> {
                // 同上
            }
            "history" -> {
                // 同上
            }
            else -> {
                focusRequester.requestFocus()
            }
        }
    }

    val searchHistory = viewModel.searchHistory

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Left Column: Input and Custom Keyboard
        Column(
            modifier = Modifier
                .weight(1.6f)
                .fillMaxHeight()
                .padding(end = 16.dp)
        ) {
            // Search Input Box
            SearchInputBox(
                searchText = searchText,
                onValueChange = { viewModel.updateSearchInput(it) },
                focusRequester = focusRequester
            )
            LaunchedEffect(lastFocusArea) {
                if (lastFocusArea == "input") {
                    focusRequester.requestFocus()
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Keyboard
            // Default focus on 'A' which is the first item in the keyboard
            CustomKeyboard(
                onKeyPress = { key ->
                    viewModel.appendToSearchInput(key)
                },
                onDelete = {
                    viewModel.deleteLastChar()
                },
                onClear = {
                    viewModel.clearSearchInput()
                },
                onSearch = {
                    viewModel.addHistory(searchText)
                    viewModel.updateSearchOrder("totalrank")
                    viewModel.search(searchText, "video")
                    onSearch(searchText)
                },
                initialFocusRequester = focusRequester
            )
        }

        // Middle Column: Results or History
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
        ) {
            if (searchText.isEmpty()) {
                // Search History
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (searchHistory.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        searchHistory.forEachIndexed { index, history ->
                            val chipFocusRequester = remember { FocusRequester() }
                            LaunchedEffect(lastFocusArea, searchHistory.size) {
                                if (lastFocusArea == "history" && lastFocusIndex == index) {
                                    chipFocusRequester.requestFocus()
                                }
                            }
                            HistoryChip(
                                text = history,
                                onClick = {
                                    val original = searchText
                                    viewModel.addHistory(history)
                                    viewModel.updateSearchOrder("totalrank")
                                    viewModel.search(history, "video")
                                    onSearch(history)
                                    viewModel.updateSearchInput(original)
                                    viewModel.updateFocus("history", index)
                                },
                                modifier = Modifier
                                    .focusRequester(chipFocusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            viewModel.updateFocus("history", index)
                                        }
                                    }
                            )
                        }
                    }
                }
            } else {
                // Search Suggestions
                Text(
                    text = "搜索建议",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                when {
                suggestions.isEmpty() -> {
                        Text(
                            text = "暂无建议",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(suggestions) { index, item ->
                                val title = item.value.ifBlank { item.term }
                                val displayText = (title.ifBlank { item.name }).replace(Regex("<.*?>"), "")
                                val itemFocusRequester = remember { FocusRequester() }
                                LaunchedEffect(lastFocusArea, suggestions.size) {
                                    if (lastFocusArea == "suggest" && lastFocusIndex == index) {
                                        itemFocusRequester.requestFocus()
                                    }
                                }
                                UnifiedListItem(
                                    text = displayText,
                                    icon = Icons.Default.Search,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = {
                                        val finalKeyword = displayText
                                        viewModel.updateSearchInput(finalKeyword)
                                        viewModel.addHistory(finalKeyword)
                                        viewModel.updateSearchOrder("totalrank")
                                        viewModel.search(finalKeyword, "video")
                                        onSearch(finalKeyword)
                                        viewModel.updateFocus("suggest", index)
                                    },
                                    modifier = Modifier
                                        .focusRequester(itemFocusRequester)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                viewModel.updateFocus("suggest", index)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Hot Search
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .padding(start = 16.dp)
        ) {
            Text(
                text = "热门搜索",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when {
                isLoadingHot -> {
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                hotSearchError != null -> {
                    Text(
                        text = hotSearchError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                hotSearches.isEmpty() -> {
                    Text(
                        text = "暂无数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(hotSearches) { index, item ->
                            val title = item.showName.ifBlank { item.keyword }
                            val itemFocusRequester = remember { FocusRequester() }
                            LaunchedEffect(lastFocusArea, hotSearches.size) {
                                if (lastFocusArea == "hot" && lastFocusIndex == index) {
                                    itemFocusRequester.requestFocus()
                                }
                            }
                            UnifiedListItem(
                                text = title,
                                icon = Icons.Default.Star,
                                iconTint = if (index < 3) Color(0xFFFF5000) else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = {
                                    val original = searchText
                                    viewModel.addHistory(title)
                                    viewModel.updateSearchOrder("totalrank")
                                    viewModel.search(title, "video")
                                    onSearch(title)
                                    viewModel.updateSearchInput(original)
                                        viewModel.updateFocus("hot", index)
                                },
                                modifier = Modifier
                                    .focusRequester(itemFocusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            viewModel.updateFocus("hot", index)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchInputBox(
    searchText: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    BasicTextField(
        value = searchText,
        onValueChange = onValueChange,
        enabled = true, // Enabled for focus and input
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource) // Explicitly focusable
            .padding(horizontal = 16.dp),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp
        ),
        singleLine = true,
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchText.isEmpty()) {
                        Text(
                            text = "搜索",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 18.sp
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
fun CustomKeyboard(
    onKeyPress: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    initialFocusRequester: FocusRequester
) {
    val rows = listOf(
        listOf("清空", "后退"),
        listOf("A", "B", "C", "D", "E", "F"),
        listOf("G", "H", "I", "J", "K", "L"),
        listOf("M", "N", "O", "P", "Q", "R"),
        listOf("S", "T", "U", "V", "W", "X"),
        listOf("Y", "Z", "1", "2", "3", "4"),
        listOf("5", "6", "7", "8", "9", "0")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Search Button Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            KeyboardButton(
                text = "搜索",
                modifier = Modifier.weight(1f),
                onClick = onSearch
            )
        }
        
        // Character Grid
        rows.forEachIndexed { rowIndex, rowKeys ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowKeys.forEachIndexed { colIndex, key ->
                    val modifier = Modifier.weight(1f)
                    // Request focus for the first item in the character rows (Row 0, Col 0 -> 'A')
                    val finalModifier = if (rowIndex == 0 && colIndex == 0) {
                        modifier.focusRequester(initialFocusRequester)
                    } else {
                        modifier
                    }

                    val onClick: () -> Unit = when {
                        rowIndex == 0 && colIndex == 0 -> { onClear } // 清空按钮
                        rowIndex == 0 && colIndex == 1 -> { onDelete } // 后退按钮
                        else -> { { onKeyPress(key) } } // 字符按钮
                    }

                    KeyboardButton(
                        text = key,
                        modifier = finalModifier,
                        onClick = onClick
                    )
                }
            }
        }
        
        // Use LaunchedEffect to request focus
        LaunchedEffect(Unit) {
            initialFocusRequester.requestFocus()
        }
    }
}

@Composable
fun KeyboardButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isIcon: Boolean = false,
    isSearchButton: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HistoryChip(text: String, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Unified List Item Component used for Results and Hot Search
 */
@Composable
fun UnifiedListItem(
    text: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // Consistent height
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
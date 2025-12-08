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
fun SearchScreen() {
    var searchText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Mock Data
    val searchHistory = listOf("opgguzi")
    val hotSearches = listOf(
        "WE vs DYG 挑战者杯",
        "杨瀚森首次首发表现如何",
        "大件运输到西藏是如何完成的",
        "湖人险胜76人",
        "猎鹰逆转G2晋级八强",
        "网警依法查处网络谣言案",
        "诺里斯成F1第35位世界冠军",
        "F1阿布扎比站维斯塔潘夺冠",
        "把济南5A景区做进游戏",
        "恒星不忘交响乐版"
    )

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
                onValueChange = { searchText = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Custom Keyboard
            // Default focus on 'A' which is the first item in the keyboard
            CustomKeyboard(
                onKeyPress = { key ->
                    searchText += key
                },
                onDelete = {
                    if (searchText.isNotEmpty()) {
                        searchText = searchText.dropLast(1)
                    }
                },
                onClear = {
                    searchText = ""
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
                        searchHistory.forEach { history ->
                            HistoryChip(text = history)
                        }
                    }
                }
            } else {
                // Search Results (Mock)
                Text(
                    text = "搜索结果",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(5) { index ->
                        UnifiedListItem(
                            text = "$searchText 相关结果 ${index + 1}",
                            icon = Icons.Default.Search,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(hotSearches) { index, item ->
                    UnifiedListItem(
                        text = item,
                        icon = Icons.Default.Star,
                        iconTint = if (index < 3) Color(0xFFFF5000) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SearchInputBox(
    searchText: String,
    onValueChange: (String) -> Unit
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
    initialFocusRequester: FocusRequester
) {
    val rows = listOf(
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            KeyboardButton(
                text = "搜索",
                modifier = Modifier.weight(2f),
                onClick = {},
                isSearchButton = true
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            KeyboardButton(
                text = "清空",
                modifier = Modifier.weight(1f),
                onClick = onClear
            )
            KeyboardButton(
                text = "后退",
                modifier = Modifier.weight(1f),
                onClick = onDelete,
                isIcon = false 
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
                    // Request focus for the very first item (Row 0, Col 0 -> 'A')
                    val finalModifier = if (rowIndex == 0 && colIndex == 0) {
                        modifier.focusRequester(initialFocusRequester)
                    } else {
                        modifier
                    }

                    KeyboardButton(
                        text = key,
                        modifier = finalModifier,
                        onClick = { onKeyPress(key) }
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
fun HistoryChip(text: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = {},
        shape = RoundedCornerShape(50),
        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
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
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
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
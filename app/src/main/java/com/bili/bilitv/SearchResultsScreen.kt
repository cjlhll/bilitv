package com.bili.bilitv

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SimpleVideoGridStateManager : VideoGridStateManager {
    private val scrollStates = mutableMapOf<Any, Pair<Int, Int>>()
    private val focusIndices = mutableMapOf<Any, Int>()
    override var shouldRestoreFocusToGrid: Boolean = false

    override fun updateScrollState(key: Any, index: Int, offset: Int) {
        scrollStates[key] = index to offset
    }

    override fun getScrollState(key: Any): Pair<Int, Int> {
        return scrollStates[key] ?: (0 to 0)
    }

    override fun updateFocusedIndex(key: Any, index: Int) {
        focusIndices[key] = index
    }

    override fun getFocusedIndex(key: Any): Int {
        return focusIndices[key] ?: -1
    }
}

@Composable
fun SearchResultInput(
    query: String,
    onSearch: (String) -> Unit
) {
    var text by remember(query) { mutableStateOf(query) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    
    var isEditing by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            readOnly = !isEditing,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .focusRequester(focusRequester)
                .background(
                    color = if (isFocused) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .onFocusChanged { 
                    if (!it.isFocused) {
                        isEditing = false
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (isFocused && !isEditing && 
                        event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || 
                         event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                        isEditing = true
                        keyboardController?.show()
                        return@onPreviewKeyEvent true
                    }
                    false
                },
            textStyle = TextStyle(
                color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    isEditing = false
                    keyboardController?.hide()
                    onSearch(text)
                }
            ),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp) // Adjusted inner padding
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp)) // Adjusted space
                    Box(modifier = Modifier.weight(1f)) {
                        innerTextField()
                    }
                }
            }
        )
    }
}

@Composable
fun SearchResultsScreen(
    query: String,
    onVideoClick: (Video) -> Unit,
    onBack: () -> Unit,
    onSearch: (String) -> Unit
) {
    // Tabs
    val tabs = listOf(
        TabItem("video", "视频"),
        TabItem("live", "直播"),
        TabItem("user", "用户")
    )
    var selectedTabId by remember { mutableStateOf(tabs[0].id) }
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("综合排序") }
    val filterOptions = listOf("综合排序", "最多播放", "最新发布", "时长较短")

    // State Manager
    val stateManager = remember { SimpleVideoGridStateManager() }

    // Mock Data - now depends on query
    val dummyVideos = remember(query) {
        List(20) { index ->
            Video(
                id = index.toString(),
                title = "搜索结果演示视频 $index - $query",
                coverUrl = "https://i0.hdslb.com/bfs/archive/placeholder.jpg", // Placeholder
                author = "UP主 $index",
                playCount = "${1000 * (index + 1)}",
                danmakuCount = "${100 * (index + 1)}",
                duration = "10:00"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Input Header
        SearchResultInput(
            query = query,
            onSearch = onSearch
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommonTabRow(
                tabs = tabs,
                selectedTab = selectedTabId,
                onTabSelected = { selectedTabId = it },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 0.dp, end = 8.dp)
            )

            Box {
                OutlinedButton(
                    onClick = { filterExpanded = true },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(text = "筛选", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = selectedFilter, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    filterOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedFilter = option
                                filterExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Video Grid
        CommonVideoGrid(
            videos = dummyVideos,
            stateManager = stateManager,
            stateKey = selectedTabId,
            onVideoClick = onVideoClick,
            horizontalSpacing = 12.dp,
            verticalSpacing = 12.dp,
            contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp)
        )
    }
}
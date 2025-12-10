package com.bili.bilitv

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    viewModel: SearchViewModel,
    onVideoClick: (Video) -> Unit,
    onLiveClick: (Video) -> Unit,
    onMediaClick: (Video) -> Unit,
    onBack: () -> Unit,
    onSearch: (String) -> Unit
) {
    // Tabs
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("综合排序") }
    var isFilterFocused by remember { mutableStateOf(false) }
    val filterOptions = listOf("综合排序", "最多播放", "最新发布", "最多弹幕", "最多收藏")

    val orderMap = mapOf(
        "综合排序" to "totalrank",
        "最多播放" to "click",
        "最新发布" to "pubdate",
        "最多弹幕" to "dm",
        "最多收藏" to "stow"
    )

    // State Manager (ViewModel级别，跨导航持久化滚动与焦点)
    val stateManager: VideoGridStateManager = viewModel

    LaunchedEffect(query) {
        if (query.isNotBlank() && viewModel.currentKeyword != query && !viewModel.isLoading) {
            viewModel.search(query)
        }
    }

    val availableTabs = buildTabsFromTypes(viewModel.availableTypes).ifEmpty {
        listOf(TabItem("video", "视频"))
    }

    val hasValidTabs = availableTabs.isNotEmpty()
    val selectedTabId = run {
        val current = viewModel.currentSearchType
        if (availableTabs.none { it.id.toString() == current }) {
            availableTabs.firstOrNull()?.id?.toString() ?: "video"
        } else {
            current
        }
    }

    LaunchedEffect(availableTabs, selectedTabId) {
        if (availableTabs.none { it.id.toString() == selectedTabId } && availableTabs.isNotEmpty()) {
            val newId = availableTabs.first().id.toString()
            if (newId != viewModel.currentSearchType) {
                viewModel.switchType(newId)
            }
        }
    }

    // 当切换到非video tab时，重置筛选状态
    LaunchedEffect(selectedTabId) {
        if (selectedTabId != "video" && selectedFilter != "综合排序") {
            selectedFilter = "综合排序"
        }
    }

    val videos = viewModel.videoResults
    val users = viewModel.userResults
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    val canLoadMore = viewModel.currentPage < viewModel.totalPages
    val isUserTab = selectedTabId == "bili_user"
    val isLiveTab = selectedTabId == "live"
    val isVerticalCardTab = selectedTabId == "media_bangumi" || selectedTabId == "media_ft"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Input Header
        SearchResultInput(
            query = query,
            onSearch = {
                viewModel.updateSearchInput(it)
                viewModel.addHistory(it)
                onSearch(it)
                viewModel.search(it)
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommonTabRow(
                tabs = availableTabs,
                selectedTab = selectedTabId,
                onTabSelected = { tabId ->
                    val idStr = tabId.toString()
                    viewModel.switchType(idStr)
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 0.dp, end = 8.dp)
            )

            if (selectedTabId == "video") {
                Box {
                    val isFilterSelected = selectedFilter != "综合排序"

                    Button(
                        onClick = { filterExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFilterSelected) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isFilterSelected) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (isFilterFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                        modifier = Modifier
                            .height(26.dp)
                            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                            .onFocusChanged { isFilterFocused = it.isFocused }
                        ,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
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
                                    val orderValue = orderMap[option] ?: "totalrank"
                                    viewModel.updateSearchOrder(orderValue)
                                    if (query.isNotBlank()) {
                                        viewModel.searchWithOrder(query, viewModel.currentSearchType, orderValue)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        val hasNoItem = if (isUserTab) users.isEmpty() else videos.isEmpty()
        val shouldShowLoading = isLoading && hasNoItem
        if (shouldShowLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "加载中…", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (hasNoItem && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无数据", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (isVerticalCardTab) {
                CommonVideoGrid(
                    items = videos,
                    stateManager = stateManager,
                    stateKey = selectedTabId,
                    columns = 5,
                    onItemClick = onMediaClick,
                    onLoadMore = if (canLoadMore) ({ viewModel.loadMoreVideos() }) else null,
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 16.dp,
                    contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp)
                ) { video, itemModifier ->
                    VerticalMediaCard(
                        video = video,
                        onClick = onMediaClick,
                        modifier = itemModifier
                    )
                }
            } else if (isLiveTab) {
                CommonVideoGrid(
                    items = videos,
                    stateManager = stateManager,
                    stateKey = selectedTabId,
                    columns = 4,
                    onItemClick = { video -> onLiveClick(video) },
                    onLoadMore = if (canLoadMore) ({ viewModel.loadMoreVideos() }) else null,
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 12.dp,
                    contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp)
                ) { video, itemModifier ->
                    LiveRoomCard(
                        room = video.toLiveRoomStub(),
                        onClick = { onLiveClick(video) },
                        modifier = itemModifier
                    )
                }
            } else if (isUserTab) {
                CommonVideoGrid(
                    items = users,
                    stateManager = stateManager,
                    stateKey = selectedTabId,
                    columns = 5,
                    onLoadMore = if (canLoadMore) ({ viewModel.loadMoreVideos() }) else null,
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 12.dp,
                    contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp)
                ) { user, itemModifier ->
                    BiliUserCard(
                        user = user,
                        modifier = itemModifier
                    )
                }
            } else {
                CommonVideoGrid(
                    videos = videos,
                    stateManager = stateManager,
                    stateKey = selectedTabId,
                    onVideoClick = onVideoClick,
                    onLoadMore = if (canLoadMore) ({ viewModel.loadMoreVideos() }) else null,
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 12.dp,
                    contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp)
                )
            }
        }
    }
}

private fun buildTabsFromTypes(types: List<String>): List<TabItem> {
    val ordered = listOf(
        "video" to "视频",
        "media_bangumi" to "番剧",
        "media_ft" to "影视",
        "live" to "直播",
        "bili_user" to "用户"
    )
    val set = types.toSet()
    return ordered.mapNotNull { (id, title) ->
        if (set.contains(id)) TabItem(id, title) else null
    }
}

private fun Video.toLiveRoomStub(): LiveRoomItem {
    val roomId = (cid.takeIf { it != 0L } ?: aid).toInt()
    val online = playCount.toIntOrNullSafe()
    return LiveRoomItem(
        roomid = roomId,
        uid = 0L,
        title = title,
        uname = author,
        online = online,
        user_cover = coverUrl,
        system_cover = coverUrl,
        cover = coverUrl,
        face = "",
        parent_id = 0,
        parent_name = "",
        area_id = 0,
        area_name = ""
    )
}

private fun String.toIntOrNullSafe(): Int {
    return this.replace(",", "").toIntOrNull() ?: 0
}

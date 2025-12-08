package com.bili.bilitv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
fun SearchResultsScreen(
    query: String,
    onVideoClick: (Video) -> Unit,
    onBack: () -> Unit
) {
    // Tabs
    val tabs = listOf(
        TabItem("video", "视频"),
        TabItem("live", "直播"),
        TabItem("user", "用户")
    )
    var selectedTabId by remember { mutableStateOf(tabs[0].id) }

    // State Manager
    val stateManager = remember { SimpleVideoGridStateManager() }

    // Mock Data
    val dummyVideos = remember {
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
        // Tab Row
        CommonTabRow(
            tabs = tabs,
            selectedTab = selectedTabId,
            onTabSelected = { selectedTabId = it }
        )

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

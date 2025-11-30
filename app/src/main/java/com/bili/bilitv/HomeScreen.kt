package com.bili.bilitv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tab页类型
 */
enum class TabType(val title: String) {
    RECOMMEND("推荐"),
    TRENDING("动态"),
    HOT("热门")
}

/**
 * 首页屏幕
 * 包含三个Tab和视频列表
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(TabType.RECOMMEND) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab栏
        TabRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 视频列表
        VideoGrid(selectedTab)
    }
}

/**
 * Tab切换栏
 */
@Composable
private fun TabRow(
    selectedTab: TabType,
    onTabSelected: (TabType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabType.entries.forEach { tab ->
            TabButton(
                text = tab.title,
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) }
            )
            if (tab != TabType.entries.last()) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

/**
 * Tab按钮
 */
@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f, label = "scale")

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary 
                          else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
        modifier = modifier
            .width(80.dp)
            .height(32.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * 视频网格列表
 */
@Composable
private fun VideoGrid(
    tabType: TabType,
    modifier: Modifier = Modifier
) {
    // 模拟数据
    val videos = remember(tabType) {
        getVideosForTab(tabType)
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier
            .fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 32.dp,
            bottom = 32.dp
        )
    ) {
        items(videos) { video ->
            VideoItem(
                video = video,
                onClick = { /* TODO: 处理视频点击 */ }
            )
        }
    }
}

/**
 * 获取对应Tab的视频数据
 */
private fun getVideosForTab(tabType: TabType): List<Video> {
    val prefix = when (tabType) {
        TabType.RECOMMEND -> "推荐视频"
        TabType.TRENDING -> "动态视频"
        TabType.HOT -> "热门视频"
    }
    
    return List(12) { index ->
        Video(
            id = "${tabType.name}_$index",
            title = "$prefix ${index + 1}: 这是一个有趣的视频标题，内容非常精彩",
            coverUrl = "", // 这里使用空字符串，会显示占位图
            author = "UP主${index + 1}",
            playCount = "${(index + 1) * 1000}次观看"
        )
    }
}

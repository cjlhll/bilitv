package com.bili.bilitv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

/**
 * 通用视频网格组件接口，用于管理滚动位置和焦点状态
 */
interface VideoGridStateManager {
    /**
     * 更新滚动状态
     * @param key 状态键（可以是tab类型、分类ID等）
     * @param index 滚动位置索引
     * @param offset 滚动偏移量
     */
    fun updateScrollState(key: Any, index: Int, offset: Int)
    
    /**
     * 获取滚动状态
     * @param key 状态键
     * @return 滚动位置和偏移量的Pair
     */
    fun getScrollState(key: Any): Pair<Int, Int>
    
    /**
     * 更新焦点索引
     * @param key 状态键
     * @param index 焦点索引
     */
    fun updateFocusedIndex(key: Any, index: Int)
    
    /**
     * 获取焦点索引
     * @param key 状态键
     * @return 焦点索引，-1表示无焦点记录
     */
    fun getFocusedIndex(key: Any): Int
    
    /**
     * 是否应该恢复焦点到网格
     */
    val shouldRestoreFocusToGrid: Boolean
}

/**
 * 通用视频网格组件
 * 
 * @param videos 视频列表
 * @param stateManager 状态管理器，用于保存和恢复滚动位置和焦点
 * @param stateKey 状态键，用于区分不同的列表状态（如不同的tab或分类）
 * @param columns 网格列数
 * @param onVideoClick 视频点击回调
 * @param onLoadMore 加载更多回调
 * @param modifier 修饰符
 * @param horizontalSpacing 水平间距
 * @param verticalSpacing 垂直间距
 * @param contentPadding 内容边距
 */
@Composable
fun <T> CommonVideoGrid(
    items: List<T>,
    stateManager: VideoGridStateManager,
    stateKey: Any,
    columns: Int = 4,
    onItemClick: (T) -> Unit = {},
    onLoadMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(top = 16.dp, bottom = 32.dp, start = 12.dp, end = 12.dp),
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit
) {
    val (initialIndex, initialOffset) = remember(stateKey) { 
        stateManager.getScrollState(stateKey) 
    }
    val initialFocusIndex = remember(stateKey) { 
        stateManager.getFocusedIndex(stateKey) 
    }
    val shouldRestoreFocus = stateManager.shouldRestoreFocusToGrid

    // 使用prefetchCount提高滚动性能,提前加载屏幕外的item
    // 4列网格,提前加载3行(12个item)可以覆盖快速滚动场景
    val listState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )

    // 监听滚动位置并保存到 StateManager
    LaunchedEffect(listState, stateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                stateManager.updateScrollState(stateKey, index, offset)
            }
    }

    // 监听滚动位置，提前触发加载更多（还剩50个item时触发）
    if (onLoadMore != null) {
        LaunchedEffect(listState) {
            var lastLoadTime = 0L
            
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                val lastIndex = lastVisibleItem?.index ?: 0
                val shouldLoad = totalItems > 0 && lastIndex >= totalItems - ImageConfig.SCROLL_PRELOAD_THRESHOLD
                
                // Emit current state including index to ensure we keep checking as user scrolls
                Triple(totalItems, lastIndex, shouldLoad)
            }
            .collect { (_, _, shouldLoad) ->
                val currentTime = System.currentTimeMillis()
                // 防止频繁触发：至少间隔500ms
                if (shouldLoad && currentTime - lastLoadTime > 500) {
                    lastLoadTime = currentTime
                    
                    // 直接调用，ViewModel会在IO线程执行
                    onLoadMore()
                }
            }
        }
    }

    LazyVerticalGrid(
        state = listState,
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        contentPadding = contentPadding
    ) {
        itemsIndexed(items) { index, item ->
            val focusRequester = remember { FocusRequester() }
            
            // 恢复焦点 - 监听shouldRestoreFocus和items列表变化
            LaunchedEffect(shouldRestoreFocus, items.size) {
                if (shouldRestoreFocus && items.isNotEmpty()) {
                    if (index == initialFocusIndex || (initialFocusIndex == -1 && index == 0)) {
                        focusRequester.requestFocus()
                    }
                }
            }

            itemContent(
                item,
                Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            stateManager.updateFocusedIndex(stateKey, index)
                        }
                    }
            )
        }
    }
}

@Composable
fun CommonVideoGrid(
    videos: List<Video>,
    stateManager: VideoGridStateManager,
    stateKey: Any,
    columns: Int = 4,
    onVideoClick: (Video) -> Unit = {},
    onLoadMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(top = 16.dp, bottom = 32.dp, start = 12.dp, end = 12.dp)
) {
    CommonVideoGrid(
        items = videos,
        stateManager = stateManager,
        stateKey = stateKey,
        columns = columns,
        onItemClick = onVideoClick,
        onLoadMore = onLoadMore,
        modifier = modifier,
        horizontalSpacing = horizontalSpacing,
        verticalSpacing = verticalSpacing,
        contentPadding = contentPadding
    ) { video, itemModifier ->
        VideoItem(
            video = video,
            onClick = onVideoClick,
            modifier = itemModifier
        )
    }
}

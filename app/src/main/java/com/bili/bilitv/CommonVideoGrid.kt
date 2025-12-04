package com.bili.bilitv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
                kotlinx.coroutines.delay(100) // 每100ms检查一次
            }
        }.collect { (index, offset) ->
            stateManager.updateScrollState(stateKey, index, offset)
        }
    }

    // 监听滚动位置，提前触发加载更多（还剩50个item时触发）
    if (onLoadMore != null) {
        LaunchedEffect(listState) {
            var lastLoadTime = 0L
            var isLoading = false
            
            kotlinx.coroutines.flow.flow {
                while (true) {
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                    val lastIndex = lastVisibleItem?.index ?: 0
                    
                    // 如果还剩50个item未显示，则触发预加载
                    // 4列网格，50个item大约12.5行，提前很多触发
                    emit(totalItems > 0 && lastIndex >= totalItems - 50)
                    kotlinx.coroutines.delay(200) // 降低检查频率为200ms，减少性能开销
                }
            }.collect { shouldLoad ->
                val currentTime = System.currentTimeMillis()
                // 防止频繁触发：至少间隔2秒，且不在加载中
                if (shouldLoad && !isLoading && currentTime - lastLoadTime > 2000) {
                    isLoading = true
                    lastLoadTime = currentTime
                    
                    // 直接调用，ViewModel会在IO线程执行
                    onLoadMore()
                    isLoading = false
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

/**
 * 视频网格专用版本 - 使用 Video 数据类型
 */
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
    val context = LocalContext.current
    
    val (initialIndex, initialOffset) = remember(stateKey) { 
        stateManager.getScrollState(stateKey) 
    }
    val initialFocusIndex = remember(stateKey) { 
        stateManager.getFocusedIndex(stateKey) 
    }
    val shouldRestoreFocus = stateManager.shouldRestoreFocusToGrid

    val listState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )
    
    // 智能预加载：根据滚动速度和位置动态预加载
    LaunchedEffect(videos, listState) {
        if (videos.isEmpty()) return@LaunchedEffect
        
        // 初始预加载首屏图片
        ImagePreloader.preloadImages(context, videos.take(ImageConfig.FIRST_SCREEN_PRELOAD_COUNT))
        
        var lastScrollPosition = listState.firstVisibleItemIndex
        var lastScrollTime = System.currentTimeMillis()
        var scrollSpeed = 0 // items per second
        
        // 监听滚动位置和速度，智能预加载
        kotlinx.coroutines.flow.flow {
            while (true) {
                val currentPosition = listState.firstVisibleItemIndex
                val currentTime = System.currentTimeMillis()
                
                // 计算滚动速度
                if (currentTime - lastScrollTime > 100) { // 至少100ms间隔
                    val timeDiff = (currentTime - lastScrollTime).toFloat() / 1000f // 转换为秒
                    val positionDiff = abs(currentPosition - lastScrollPosition)
                    scrollSpeed = (positionDiff / timeDiff).toInt()
                    
                    lastScrollPosition = currentPosition
                    lastScrollTime = currentTime
                }
                
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val firstVisible = visibleItems.firstOrNull()?.index ?: 0
                val lastVisible = visibleItems.lastOrNull()?.index ?: 0
                
                // 根据滚动速度调整预加载范围
                val preloadRange = when {
                    scrollSpeed > ImageConfig.FAST_SCROLL_THRESHOLD * 2 -> 8 // 快速滚动：预加载8行
                    scrollSpeed > ImageConfig.FAST_SCROLL_THRESHOLD -> 6    // 中速滚动：预加载6行
                    else -> ImageConfig.SCROLL_PRELOAD_ROWS                // 慢速滚动：预加载2行
                }
                
                val preloadRows = preloadRange * columns
                val preloadStart = max(0, firstVisible - preloadRows)
                val preloadEnd = min(videos.size - 1, lastVisible + preloadRows)
                
                emit(Triple(preloadStart, preloadEnd, scrollSpeed))
                kotlinx.coroutines.delay(ImageConfig.SCROLL_SPEED_CHECK_INTERVAL) // 降低检查频率
            }
        }.collect { (start, end, speed) ->
            if (start <= end) {
                val videosToPreload = videos.slice(start..end)
                
                // 根据滚动速度调整预加载优先级
                if (speed > ImageConfig.FAST_SCROLL_THRESHOLD) {
                    // 快速滚动时，只预加载即将显示的部分
                    val immediateRange = min(ImageConfig.FIRST_SCREEN_PRELOAD_COUNT, videosToPreload.size)
                    ImagePreloader.preloadImages(context, videosToPreload.take(immediateRange))
                } else {
                    // 慢速滚动时，预加载全部范围
                    ImagePreloader.preloadImages(context, videosToPreload)
                }
            }
        }
    }
    
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

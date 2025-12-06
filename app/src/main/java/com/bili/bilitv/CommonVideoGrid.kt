package com.bili.bilitv

import android.util.Log
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bili.bilitv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
    state: androidx.compose.foundation.lazy.grid.LazyGridState? = null,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit
) {
    val (initialIndex, initialOffset) = remember(stateKey) { 
        stateManager.getScrollState(stateKey) 
    }
    val initialFocusIndex = remember(stateKey) { 
        stateManager.getFocusedIndex(stateKey) 
    }
    val shouldRestoreFocus = stateManager.shouldRestoreFocusToGrid

    // Use provided state or create a new one
    val listState = state ?: rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )

    // If state was provided, we might need to restore position manually if it wasn't initialized with our values
    // But typically the caller should handle initialization if they provide state.
    // To be safe, we can rely on the caller to initialize it correctly or just ignore for now as we are the only caller.
    // Actually, rememberLazyGridState with initial values is the standard way.
    // If we pass a state from outside, we should probably initialize it outside.
    
    // Let's keep it simple: The generic one uses the passed state OR creates one.
    
    // ... rest of the function uses listState ...


    // 监听滚动位置并保存到 StateManager
    LaunchedEffect(listState, stateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                stateManager.updateScrollState(stateKey, index, offset)
            }
    }

    // 优化的提前加载触发逻辑 - 滚动到底部时加载下一页
    if (onLoadMore != null) {
        // 使用 derivedStateOf 避免滚动时重计算
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                
                // 触发条件：当滚动到倒数第二行时加载下一页
                // 一行4个，所以当最后可见项索引 >= 总数 - 8 时触发
                val shouldPreload = totalItems > 0 && lastVisibleItemIndex >= totalItems - 8
                
                if (BuildConfig.DEBUG) {
                    // 仅在调试时记录，避免生产环境性能影响
                    if (shouldPreload) {
                        Log.d("BiliTV", "Preload triggered: lastVisibleItemIndex=$lastVisibleItemIndex, totalItems=$totalItems")
                    }
                }
                
                shouldPreload
            }
        }

        // 使用 LaunchedEffect + snapshotFlow 监听滚动状态
        LaunchedEffect(listState) {
            var lastLoadTime = 0L
            
            snapshotFlow { shouldLoadMore }
                .collect { shouldLoad ->
                    val currentTime = System.currentTimeMillis()
                    
                    // 防止频繁触发：至少间隔500ms
                    if (shouldLoad && currentTime - lastLoadTime > 500) {
                        lastLoadTime = currentTime
                        
                        // 在后台协程中调用，避免阻塞UI
                        launch(Dispatchers.IO) {
                            try {
                                onLoadMore()
                                if (BuildConfig.DEBUG) {
                                    Log.d("BiliTV", "Load more executed successfully")
                                }
                            } catch (e: Exception) {
                                Log.e("BiliTV", "Error in preload", e)
                            }
                        }
                    }
                }
        }

        // 兜底逻辑：当滚动停止且接近底部时也触发加载
        LaunchedEffect(listState) {
            var fallbackLastLoadTime = 0L
            var wasScrolling = false
            
            snapshotFlow { 
                val layoutInfo = listState.layoutInfo
                val isScrollingInProgress = layoutInfo.viewportStartOffset != 0 || layoutInfo.viewportEndOffset != 0
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = layoutInfo.totalItemsCount
                
                Triple(lastVisibleItemIndex, isScrollingInProgress, wasScrolling)
            }
            .collect { (lastIndex, isScrolling, wasScrollingBefore) ->
                // 记录滚动状态
                if (isScrolling) {
                    wasScrolling = true
                }
                
                // 当滚动停止且接近底部时触发
                val totalItems = listState.layoutInfo.totalItemsCount
                if (!isScrolling && wasScrollingBefore && lastIndex >= totalItems - 4) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - fallbackLastLoadTime > 1000) {
                        fallbackLastLoadTime = currentTime
                        wasScrolling = false
                        
                        launch(Dispatchers.IO) {
                            try {
                                onLoadMore()
                                if (BuildConfig.DEBUG) {
                                    Log.d("BiliTV", "Fallback load more executed")
                                }
                            } catch (e: Exception) {
                                Log.e("BiliTV", "Error in fallback preload", e)
                            }
                        }
                    }
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
        contentPadding = contentPadding,
        userScrollEnabled = true
    ) {
        itemsIndexed(
            items = items,
            key = { index, item -> 
                // 使用稳定的 key 确保列表项正确重组
                when (item) {
                    is Video -> item.id
                    else -> "${index}_${item.hashCode()}"
                }
            }
        ) { index, item ->
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
    
    val listState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )
    
    // Scroll-based Image Preloading
    LaunchedEffect(listState, videos) {
        var lastHandledIndex = -1
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            visibleItems.lastOrNull()?.index ?: -1
        }.collect { lastIndex ->
            if (lastIndex < 0 || lastIndex == lastHandledIndex) return@collect
            lastHandledIndex = lastIndex
            // Calculate preload range: N items AFTER the last visible item
            val start = lastIndex + 1
            val end = min(videos.size, start + ImageConfig.SCROLL_PRELOAD_AHEAD_COUNT)
            
            if (start < end) {
                val itemsToPreload = videos.subList(start, end)
                ImagePreloader.preloadImages(context, itemsToPreload)
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
        contentPadding = contentPadding,
        state = listState
    ) { video, itemModifier ->
        VideoItem(
            video = video,
            onClick = onVideoClick,
            modifier = itemModifier
        )
    }
}

package com.bili.bilitv

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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

@Composable
fun RefreshingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "刷新中…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

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
    scrollToTopSignal: Int = 0,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit
) {
    val (initialIndex, initialOffset) = remember(stateKey, scrollToTopSignal) { 
        stateManager.getScrollState(stateKey) 
    }
    val initialFocusIndex = remember(stateKey, scrollToTopSignal) { 
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
        // 使用 derivedStateOf 避免滚动时重计算，优化快速滚动场景
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                
                // 增大预加载阈值：当滚动到倒数第三行时就触发，适应快速滚动
                // 一行4个，所以当最后可见项索引 >= 总数 - 12 时触发
                val shouldPreload = totalItems > 0 && lastVisibleItemIndex >= totalItems - 12
                
                if (BuildConfig.DEBUG) {
                    // 仅在调试时记录，避免生产环境性能影响
                    if (shouldPreload) {
                        Log.d("BiliTV", "Preload triggered: lastVisibleItemIndex=$lastVisibleItemIndex, totalItems=$totalItems")
                    }
                }
                
                shouldPreload
            }
        }

        // 使用 LaunchedEffect + snapshotFlow 监听滚动状态，优化时间间隔
        LaunchedEffect(listState) {
            var lastLoadTime = 0L
            
            snapshotFlow { shouldLoadMore }
                .collect { shouldLoad ->
                    val currentTime = System.currentTimeMillis()
                    
                    // 减少时间间隔限制：从500ms改为200ms，适应快速滚动
                    if (shouldLoad && currentTime - lastLoadTime > 200) {
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

        // 兜底逻辑：当滚动停止且接近底部时也触发加载，优化检测逻辑
        LaunchedEffect(listState) {
            var fallbackLastLoadTime = 0L
            var wasScrolling = false
            var scrollVelocity = 0f
            var lastScrollIndex = 0
            
            snapshotFlow { 
                val layoutInfo = listState.layoutInfo
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = layoutInfo.totalItemsCount
                
                // 计算滚动速度
                val currentVelocity = (lastVisibleItemIndex - lastScrollIndex).toFloat()
                lastScrollIndex = lastVisibleItemIndex
                
                Triple(lastVisibleItemIndex, currentVelocity, wasScrolling)
            }
            .collect { (lastIndex, currentVelocity, wasScrollingBefore) ->
                // 记录滚动状态
                if (currentVelocity != 0f) {
                    wasScrolling = true
                    scrollVelocity = currentVelocity
                }
                
                // 当滚动停止且接近底部时触发，降低阈值
                val totalItems = listState.layoutInfo.totalItemsCount
                if (currentVelocity == 0f && wasScrollingBefore && lastIndex >= totalItems - 6) {
                    val currentTime = System.currentTimeMillis()
                    // 减少时间间隔：从1000ms改为500ms
                    if (currentTime - fallbackLastLoadTime > 500) {
                        fallbackLastLoadTime = currentTime
                        wasScrolling = false
                        
                        launch(Dispatchers.IO) {
                            try {
                                onLoadMore()
                                if (BuildConfig.DEBUG) {
                                    Log.d("BiliTV", "Fallback load more executed, velocity=$scrollVelocity")
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

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            listState.scrollToItem(0)
            stateManager.updateScrollState(stateKey, 0, 0)
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
    contentPadding: PaddingValues = PaddingValues(top = 16.dp, bottom = 32.dp, start = 12.dp, end = 12.dp),
    scrollToTopSignal: Int = 0
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
        state = listState,
        scrollToTopSignal = scrollToTopSignal
    ) { video, itemModifier ->
        VideoItem(
            video = video,
            onClick = onVideoClick,
            modifier = itemModifier
        )
    }
}

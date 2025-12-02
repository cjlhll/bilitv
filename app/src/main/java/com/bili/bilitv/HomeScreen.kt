package com.bili.bilitv

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private val httpClient = OkHttpClient()
private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class PopularVideoResponse(
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: PopularVideoData? = null
)

@Serializable
data class PopularVideoData(
    val list: List<VideoItemData>
)

@Serializable
data class VideoItemData(
    val bvid: String,
    val cid: Long,
    val title: String,
    val pic: String, // cover image URL
    val owner: Owner,
    val stat: Stat,
    val pubdate: Long
)

@Serializable
data class Owner(
    val mid: Long,
    val name: String
)

@Serializable
data class Stat(
    val view: Int,
    val like: Int,
    val danmaku: Int
)

/**
 * Tab页类型
 */
enum class TabType(val title: String) {
    RECOMMEND("推荐"),
    HOT("热门")
}

/**
 * 首页屏幕
 * 包含三个Tab和视频列表
 */
@OptIn(ExperimentalSerializationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onEnterFullScreen: (VideoPlayInfo, String) -> Unit = { _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()
    
    // 播放状态
    var currentPlayInfo by remember { mutableStateOf<VideoPlayInfo?>(null) }
    var currentVideoTitle by remember { mutableStateOf("") }

    // 处理视频点击
    val handleVideoClick: (Video) -> Unit = { video ->
        if (video.bvid.isNotEmpty() && video.cid != 0L) {
            Log.d("BiliTV", "Video clicked: ${video.title} (bvid=${video.bvid}, cid=${video.cid})")
            coroutineScope.launch {
                val playInfo = VideoPlayUrlFetcher.fetchPlayUrl(
                    bvid = video.bvid,
                    cid = video.cid,
                    qn = 80, // 1080P - 非大会员最高清晰度
                    fnval = 4048 // DASH格式
                )
                
                if (playInfo != null) {
                    Log.d("BiliTV", "Play URL fetched successfully:")
                    Log.d("BiliTV", "  Format: ${playInfo.format}")
                    Log.d("BiliTV", "  Quality: ${playInfo.quality}")
                    Log.d("BiliTV", "  Duration: ${playInfo.duration}s")
                    Log.d("BiliTV", "  Video URL: ${playInfo.videoUrl.take(100)}...")
                    if (playInfo.audioUrl != null) {
                        Log.d("BiliTV", "  Audio URL: ${playInfo.audioUrl.take(100)}...")
                    }
                    // 记录进入全屏状态，以便返回时恢复焦点
                    viewModel.onEnterFullScreen()
                    // 进入全屏播放
                    onEnterFullScreen(playInfo, video.title)
                } else {
                    Log.e("BiliTV", "Failed to fetch play URL")
                }
            }
        } else {
            Log.w("BiliTV", "Video missing bvid or cid: ${video.title}")
        }
    }

    LaunchedEffect(viewModel.selectedTab) {
        if (viewModel.selectedTab == TabType.HOT && viewModel.hotVideos.isEmpty()) {
            viewModel.loadMoreHot()
        }
    }
    
    // 显示视频列表
    Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab栏
            TabRow(
                selectedTab = viewModel.selectedTab,
                onTabSelected = { viewModel.onTabChanged(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 视频列表
            val videosToDisplay = remember(viewModel.selectedTab, viewModel.hotVideos, viewModel.recommendVideos) {
                when (viewModel.selectedTab) {
                    TabType.HOT -> viewModel.hotVideos.map { mapVideoItemDataToVideo(it) }
                    TabType.RECOMMEND -> viewModel.recommendVideos.map { mapVideoItemDataToVideo(it) }
                    else -> getVideosForTab(viewModel.selectedTab)
                }
            }
            
            // 使用 key 确保切换 Tab 时重新创建 Grid，从而应用新的初始滚动位置
            key(viewModel.selectedTab) {
                VideoGrid(
                    videos = videosToDisplay,
                    onVideoClick = handleVideoClick,
                    viewModel = viewModel,
                    onLoadMore = {
                        when (viewModel.selectedTab) {
                            TabType.RECOMMEND -> viewModel.loadMoreRecommend()
                            TabType.HOT -> viewModel.loadMoreHot()
                        }
                    }
                )
            }
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
    videos: List<Video>,
    onVideoClick: (Video) -> Unit = {},
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {}
) {
    val currentTab = viewModel.selectedTab
    val (initialIndex, initialOffset) = remember(currentTab) { viewModel.getScrollState(currentTab) }
    val initialFocusIndex = remember(currentTab) { viewModel.getFocusedIndex(currentTab) }
    val shouldRestoreFocus = viewModel.shouldRestoreFocusToGrid

    val listState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )

    // 监听滚动位置并保存到 ViewModel
    LaunchedEffect(listState, currentTab) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.updateScrollState(currentTab, index, offset)
            }
    }

    // 监听滚动到底部
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val lastIndex = lastVisibleItem?.index ?: 0
            
            // 如果最后一个可见项接近总数（例如倒数第4个），则触发加载
            totalItems > 0 && lastIndex >= totalItems - 4
        }.collect { shouldLoad ->
            if (shouldLoad) {
                onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        state = listState,
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
        itemsIndexed(videos) { index, video ->
            val focusRequester = remember { FocusRequester() }
            
            // 恢复焦点
            LaunchedEffect(shouldRestoreFocus) {
                if (shouldRestoreFocus) {
                    if (index == initialFocusIndex || (initialFocusIndex == -1 && index == 0)) {
                        focusRequester.requestFocus()
                    }
                }
            }

            VideoItem(
                video = video,
                onClick = onVideoClick,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            viewModel.updateFocusedIndex(currentTab, index)
                        }
                    }
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

private fun mapVideoItemDataToVideo(videoItemData: VideoItemData): Video {
    return Video(
        id = videoItemData.bvid,
        bvid = videoItemData.bvid,
        cid = videoItemData.cid,
        title = videoItemData.title,
        coverUrl = videoItemData.pic,
        author = videoItemData.owner.name,
        playCount = "${videoItemData.stat.view}次观看", // Format play count
        pubDate = videoItemData.pubdate
    )
}

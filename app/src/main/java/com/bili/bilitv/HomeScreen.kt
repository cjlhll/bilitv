package com.bili.bilitv

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.bili.bilitv.BuildConfig
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
    val aid: Long = 0, // 视频AV号
    val bvid: String,
    val cid: Long,
    val title: String,
    val pic: String, // cover image URL
    val owner: Owner,
    val stat: Stat,
    val pubdate: Long,
    val duration: Long = 0 // Add duration field
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
    var isVisible by remember { mutableStateOf(false) }
    
    // 进入动画
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // 播放状态
    var currentPlayInfo by remember { mutableStateOf<VideoPlayInfo?>(null) }
    var currentVideoTitle by remember { mutableStateOf("") }

    // 处理视频点击
    val handleVideoClick: (Video) -> Unit = { video ->
        if (video.bvid.isNotEmpty() && video.cid != 0L) {
            if (BuildConfig.DEBUG) {
                Log.d("BiliTV", "Video clicked: ${video.title} (bvid=${video.bvid}, cid=${video.cid})")
            }
            coroutineScope.launch {
                val playInfo = VideoPlayUrlFetcher.fetchPlayUrl(
                    aid = video.aid,
                    bvid = video.bvid,
                    cid = video.cid,
                    qn = 80, // 1080P - 非大会员最高清晰度
                    fnval = 4048, // DASH格式
                    cookie = SessionManager.getCookieString()
                )
                
                if (playInfo != null) {
                    if (BuildConfig.DEBUG) {
                        Log.d("BiliTV", "Play URL fetched successfully:")
                        Log.d("BiliTV", "  Format: ${playInfo.format}")
                        Log.d("BiliTV", "  Quality: ${playInfo.quality}")
                        Log.d("BiliTV", "  Duration: ${playInfo.duration}s")
                        Log.d("BiliTV", "  Video URL: ${playInfo.videoUrl.take(100)}...")
                        if (playInfo.audioUrl != null) {
                            Log.d("BiliTV", "  Audio URL: ${playInfo.audioUrl.take(100)}...")
                        }
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
        when (viewModel.selectedTab) {
            TabType.HOT -> {
                if (viewModel.hotVideos.isEmpty() && viewModel.canLoadMore(TabType.HOT)) {
                    viewModel.loadMoreHot()
                }
            }
            TabType.RECOMMEND -> {
                if (viewModel.recommendVideos.isEmpty() && viewModel.canLoadMore(TabType.RECOMMEND)) {
                    viewModel.loadMoreRecommend()
                }
            }
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
    // 显示视频列表
    Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = 8.dp) // 统一顶部间距
        ) {
            // Tab栏
            CommonTabRowWithEnum(
                tabs = TabType.entries.toTypedArray(),
                selectedTab = viewModel.selectedTab,
                onTabSelected = { viewModel.onTabChanged(it) }
            )

            // 视频列表
            val videosToDisplay = remember(viewModel.selectedTab, viewModel.hotVideos, viewModel.recommendVideos) {
                when (viewModel.selectedTab) {
                    TabType.HOT -> viewModel.hotVideos.map { mapVideoItemDataToVideo(it) }
                    TabType.RECOMMEND -> viewModel.recommendVideos.map { mapVideoItemDataToVideo(it) }
                    else -> getVideosForTab(viewModel.selectedTab)
                }
            }
            
            // 检查加载状态
            val isLoading = when (viewModel.selectedTab) {
                TabType.HOT -> viewModel.isHotLoading && viewModel.hotVideos.isEmpty()
                TabType.RECOMMEND -> viewModel.isRecommendLoading && viewModel.recommendVideos.isEmpty()
            }
            
            // 内容区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    // 使用 key 确保切换 Tab 时重新创建 Grid，从而应用新的初始滚动位置
                    key(viewModel.selectedTab) {
                        CommonVideoGrid(
                            videos = videosToDisplay,
                            stateManager = viewModel,
                            stateKey = viewModel.selectedTab,
                            columns = 4,
                            onVideoClick = handleVideoClick,
                            onLoadMore = {
                                when (viewModel.selectedTab) {
                                    TabType.RECOMMEND -> {
                                        if (viewModel.canLoadMore(TabType.RECOMMEND)) {
                                            viewModel.resetTargetCount(TabType.RECOMMEND)
                                            viewModel.loadMoreRecommend()
                                        }
                                    }
                                    TabType.HOT -> {
                                        if (viewModel.canLoadMore(TabType.HOT)) {
                                            viewModel.resetTargetCount(TabType.HOT)
                                            viewModel.loadMoreHot()
                                        }
                                    }
                                }
                            },
                            horizontalSpacing = 12.dp,
                            verticalSpacing = 12.dp,
                            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp, start = 12.dp, end = 12.dp) // 统一顶部间距
                        )
                    }
                }
            }
        }
    }
}

// 使用通用选项卡组件，无需重复定义

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
            playCount = "${(index + 1) * 1000}",
            danmakuCount = "${(index + 1) * 100}",
            duration = String.format("%02d:%02d", (index + 1), (index * 10) % 60)
        )
    }
}

private fun mapVideoItemDataToVideo(videoItemData: VideoItemData): Video {
    return Video(
        id = videoItemData.bvid,
        aid = videoItemData.aid,
        bvid = videoItemData.bvid,
        cid = videoItemData.cid,
        title = videoItemData.title,
        coverUrl = videoItemData.pic,
        author = videoItemData.owner.name,
        playCount = formatCount(videoItemData.stat.view),
        danmakuCount = formatCount(videoItemData.stat.danmaku),
        duration = formatDuration(videoItemData.duration),
        pubDate = videoItemData.pubdate
    )
}

private fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> String.format("%.1f万", count / 10000f)
        else -> count.toString()
    }
}

private fun formatDuration(seconds: Long): String {
    val totalSeconds = seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

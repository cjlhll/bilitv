package com.bili.bilitv

import android.util.Log
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bili.bilitv.BuildConfig
import com.bili.bilitv.RefreshingIndicator
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
    BANGUMI("番剧"),
    HOT("热门")
}

private fun dayLabel(day: TimelineDay): String {
    if (day.isToday) return "最近更新"
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    val idx = (day.dayOfWeek - 1).coerceIn(0, names.lastIndex)
    return "周${names[idx]}"
}

private fun TimelineEpisode.toVideoCard(): Video {
    return Video(
        id = "ep_$episodeId",
        cid = episodeId,
        title = title.ifBlank { episodeIndex },
        coverUrl = cover,
        author = "",
        playCount = formatCount(viewCount),
        duration = publishTime,
        seasonId = seasonId,
        isFollow = isFollowed
    )
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
                    val targetPlayInfo = if (video.durationSeconds > 0) {
                        playInfo.copy(duration = video.durationSeconds)
                    } else {
                        playInfo
                    }
                    onEnterFullScreen(targetPlayInfo, video.title)
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
                    coroutineScope.launch {
                        viewModel.loadNextPage(TabType.HOT)
                    }
                }
            }
            TabType.RECOMMEND -> {
                if (viewModel.recommendVideos.isEmpty() && viewModel.canLoadMore(TabType.RECOMMEND)) {
                    coroutineScope.launch {
                        viewModel.loadNextPage(TabType.RECOMMEND)
                    }
                }
            }
            TabType.BANGUMI -> {
                viewModel.ensureTimelineLoaded()
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
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU &&
                        event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                    ) {
                        viewModel.refreshCurrentTab()
                        true
                    } else {
                        false
                    }
                }
        ) {
            // Tab栏
            CommonTabRowWithEnum(
                tabs = TabType.entries.toTypedArray(),
                selectedTab = viewModel.selectedTab,
                onTabSelected = {
                    if (it == viewModel.selectedTab) {
                        viewModel.refreshCurrentTab(restoreFocusToGrid = false)
                    } else {
                        viewModel.onTabChanged(it)
                    }
                }
            )

            if (viewModel.isRefreshing) {
                RefreshingIndicator()
            }

            // 视频列表
            // 使用 derivedStateOf 避免滚动时重计算
            val videosToDisplay by remember {
                derivedStateOf {
                    when (viewModel.selectedTab) {
                        TabType.HOT -> viewModel.hotVideos.map { mapVideoItemDataToVideo(it) }
                        TabType.RECOMMEND -> viewModel.recommendVideos.map { mapVideoItemDataToVideo(it) }
                        TabType.BANGUMI -> emptyList()
                    }
                }
            }
            
            // 检查加载状态
            val isLoading = when (viewModel.selectedTab) {
                TabType.HOT -> viewModel.isHotLoading && viewModel.hotVideos.isEmpty()
                TabType.RECOMMEND -> viewModel.isRecommendLoading && viewModel.recommendVideos.isEmpty()
                TabType.BANGUMI -> viewModel.isTimelineLoading && viewModel.timelineDays.isEmpty()
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
                    if (viewModel.selectedTab == TabType.BANGUMI) {
                        BangumiTimelineSection(
                            viewModel = viewModel
                        )
                    } else {
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
                                                coroutineScope.launch {
                                                    viewModel.loadNextPage(TabType.RECOMMEND)
                                                }
                                            }
                                        }
                                        TabType.HOT -> {
                                            if (viewModel.canLoadMore(TabType.HOT)) {
                                                coroutineScope.launch {
                                                    viewModel.loadNextPage(TabType.HOT)
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
                                },
                                horizontalSpacing = 12.dp,
                                verticalSpacing = 12.dp,
                                contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp),
                                scrollToTopSignal = viewModel.refreshSignal
                            )
                        }
                    }
                }
            }
        }
    }
}

// 使用通用选项卡组件，无需重复定义

private fun mapVideoItemDataToVideo(videoItemData: VideoItemData): Video {
    return Video(
        id = videoItemData.bvid,
        aid = videoItemData.aid,
        bvid = videoItemData.bvid,
        cid = videoItemData.cid,
        title = videoItemData.title,
        coverUrl = videoItemData.pic,
        author = videoItemData.owner.name,
        playCount = videoItemData.stat.view.toString(),
        danmakuCount = videoItemData.stat.danmaku.toString(),
        duration = formatDuration(videoItemData.duration),
        durationSeconds = videoItemData.duration,
        pubDate = videoItemData.pubdate
    )
}

private fun formatCount(count: Long): String {
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

@Composable
private fun BangumiTimelineSection(
    viewModel: HomeViewModel
) {
    val days = viewModel.timelineDays
    val selectedIndex = viewModel.timelineSelectedIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val currentDay = days.getOrNull(selectedIndex)
    val isLoading = viewModel.isTimelineLoading
    val error = viewModel.timelineError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (days.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex])
                    )
                }
            ) {
                days.forEachIndexed { index, day ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { viewModel.timelineSelectedIndex = index },
                        text = {
                            val todayLabel = if (day.isToday) "今天" else ""
                            Text("${day.date} ${if (todayLabel.isNotEmpty()) todayLabel else "周${day.dayOfWeek}"}")
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading && days.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null && days.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "加载失败：$error")
                        Button(onClick = { viewModel.refreshCurrentTab() }) {
                            Text("重试")
                        }
                    }
                }
            }
            currentDay == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无数据")
                }
            }
            else -> {
                if (error != null) {
                    Text(
                        text = "部分数据加载异常：$error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val episodes = currentDay.episodes
                LazyRow(
                    modifier = Modifier
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    items(episodes) { episode ->
                        VerticalMediaCard(
                            video = episode.toVideoCard(),
                            onClick = { /* TODO: 番剧详情 */ },
                            bottomContent = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = episode.title.ifBlank { episode.episodeIndex },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = episode.episodeIndex,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                        Text(
                                            text = episode.publishTime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
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
    onEnterFullScreen: (VideoPlayInfo, String) -> Unit = { _, _ -> },
    onMediaClick: (Video) -> Unit = {},
    onNavigateToAnimeList: () -> Unit = {}
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
                viewModel.ensureBangumiRecommendLoaded()
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
                        BangumiTabContent(
                            viewModel = viewModel,
                            onMediaClick = {
                                viewModel.shouldRestoreFocusToGrid = true
                                onMediaClick(it)
                            },
                            onHeaderClick = onNavigateToAnimeList
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
private fun BangumiTabContent(
    viewModel: HomeViewModel,
    onMediaClick: (Video) -> Unit,
    onHeaderClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val recommendList = viewModel.bangumiRecommendVideos
    val isRecommendLoading = viewModel.isBangumiRecommendLoading
    val recommendError = viewModel.bangumiRecommendError
    val (initialIndex, initialOffset) = remember { viewModel.getScrollState(TabType.BANGUMI) }
    val initialFocusIndex = remember { viewModel.getFocusedIndex(TabType.BANGUMI) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )
    val shouldRestoreFocus = viewModel.shouldRestoreFocusToGrid

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                viewModel.updateScrollState(TabType.BANGUMI, idx, offset)
            }
    }

    if (viewModel.canLoadMoreBangumiRecommend()) {
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = gridState.layoutInfo
                val total = layoutInfo.totalItemsCount
                val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && last >= total - 10
            }
        }
        LaunchedEffect(gridState) {
            snapshotFlow { shouldLoadMore }.collect { need ->
                if (need && viewModel.canLoadMoreBangumiRecommend()) {
                    coroutineScope.launch {
                        viewModel.loadBangumiRecommend(reset = false)
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel.refreshSignal) {
        gridState.scrollToItem(0)
        viewModel.updateScrollState(TabType.BANGUMI, 0, 0)
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            BangumiTimelineSection(
                viewModel = viewModel,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            var isHeaderFocused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .focusable()
                        .onFocusChanged { isHeaderFocused = it.isFocused }
                        .clickable(onClick = onHeaderClick)
                        .background(
                            color = if (isHeaderFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "推荐",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isHeaderFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "更多",
                        modifier = Modifier.size(16.dp),
                        tint = if (isHeaderFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
                    )
                }
                
                if (isRecommendLoading && recommendList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (recommendError != null && recommendList.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "加载失败：$recommendError")
                        Button(onClick = { coroutineScope.launch { viewModel.loadBangumiRecommend(reset = true) } }) {
                            Text("重试")
                        }
                    }
                } else if (recommendList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无推荐")
                    }
                }
            }
        }

        itemsIndexed(
            items = recommendList,
            key = { index, video -> "bangumi_rcmd_${index}_${video.id}" }
        ) { index, video ->
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(shouldRestoreFocus, recommendList.size) {
                if (shouldRestoreFocus && recommendList.isNotEmpty()) {
                    if (index == initialFocusIndex || (initialFocusIndex == -1 && index == 0)) {
                        focusRequester.requestFocus()
                    }
                }
            }
            VerticalMediaCard(
                video = video,
                onClick = {
                    viewModel.shouldRestoreFocusToGrid = true
                    onMediaClick(video)
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            viewModel.updateFocusedIndex(TabType.BANGUMI, index)
                        }
                    }
            )
        }

        if (isRecommendLoading && recommendList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp, vertical = 8.dp)
                )
            }
        } else if (recommendError != null && recommendList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "部分数据加载异常：$recommendError",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BangumiTimelineSection(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val days = viewModel.timelineDays
    val selectedIndex = viewModel.timelineSelectedIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val currentDay = days.getOrNull(selectedIndex)
    val isLoading = viewModel.isTimelineLoading
    val error = viewModel.timelineError

    Column(
        modifier = modifier
            .fillMaxWidth()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null && days.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
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
                    modifier = Modifier.fillMaxWidth(),
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
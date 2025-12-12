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
import kotlinx.coroutines.delay
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
    HOT("热门"),
    BANGUMI("番剧"),
    CINEMA("影视")
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
    onNavigateToAnimeList: () -> Unit = {},
    onNavigateToGuochuangList: () -> Unit = {},
    onNavigateToCinemaList: (String) -> Unit = {}
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
                viewModel.ensureBangumiTabLoaded()
            }
            TabType.CINEMA -> {
                viewModel.ensureCinemaTabLoaded()
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
                        TabType.CINEMA -> emptyList()
                    }
                }
            }
            
            // 检查加载状态
            val isLoading = when (viewModel.selectedTab) {
                TabType.HOT -> viewModel.isHotLoading && viewModel.hotVideos.isEmpty()
                TabType.RECOMMEND -> viewModel.isRecommendLoading && viewModel.recommendVideos.isEmpty()
                TabType.BANGUMI -> viewModel.isTimelineLoading && viewModel.timelineDays.isEmpty()
                TabType.CINEMA -> viewModel.isCinemaTabLoading && viewModel.cinemaTabModules.isEmpty()
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
                    when (viewModel.selectedTab) {
                        TabType.BANGUMI -> {
                            BangumiTabContent(
                                viewModel = viewModel,
                                onMediaClick = {
                                    viewModel.shouldRestoreFocusToGrid = true
                                    onMediaClick(it)
                                },
                                onHeaderClick = onNavigateToAnimeList,
                                onGuochuangHeaderClick = onNavigateToGuochuangList
                            )
                        }
                        TabType.CINEMA -> {
                            CinemaTabContent(
                                viewModel = viewModel,
                                onMediaClick = {
                                    viewModel.shouldRestoreFocusToGrid = true
                                    onMediaClick(it)
                                },
                                onNavigateToCinemaList = onNavigateToCinemaList
                            )
                        }
                        else -> {
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
    onHeaderClick: () -> Unit,
    onGuochuangHeaderClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val modules = viewModel.bangumiTabModules
    val isLoading = viewModel.isBangumiTabLoading
    val error = viewModel.bangumiTabError
    val (initialIndex, initialOffset) = remember(viewModel.shouldRestoreFocusToGrid) { 
        val state = viewModel.getScrollState(TabType.BANGUMI)
        Log.d("BiliTV_Focus", "BangumiTab: getScrollState index=${state.first} offset=${state.second} restore=${viewModel.shouldRestoreFocusToGrid}")
        state
    }
    val initialFocusIndex = remember(viewModel.shouldRestoreFocusToGrid) { 
        val idx = viewModel.getFocusedIndex(TabType.BANGUMI)
        Log.d("BiliTV_Focus", "BangumiTab: getFocusedIndex index=$idx restore=${viewModel.shouldRestoreFocusToGrid}")
        idx
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )
    val shouldRestoreFocus = viewModel.shouldRestoreFocusToGrid

    // 记录是否是初次组合
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                // Log.d("BiliTV_Focus", "BangumiTab: Scroll update $idx $offset")
                viewModel.updateScrollState(TabType.BANGUMI, idx, offset)
            }
    }

    LaunchedEffect(viewModel.refreshSignal) {
        if (isFirstComposition) {
            isFirstComposition = false
            Log.d("BiliTV_Focus", "BangumiTab: Initial composition, skipping refresh reset")
            return@LaunchedEffect
        }
        Log.d("BiliTV_Focus", "BangumiTab: Refresh signal received")
        gridState.scrollToItem(0)
        viewModel.updateScrollState(TabType.BANGUMI, 0, 0)
    }

    LaunchedEffect(modules.size) {
        Log.d("BiliTV_Focus", "BangumiTab: Modules loaded size=${modules.size} initialIndex=$initialIndex")
        if (modules.isNotEmpty() && initialIndex > 0) {
             Log.d("BiliTV_Focus", "BangumiTab: Restoring scroll to $initialIndex")
             gridState.scrollToItem(initialIndex, initialOffset)
        }
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

        if (isLoading && modules.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (error != null && modules.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "加载失败：$error")
                    Button(onClick = { coroutineScope.launch { viewModel.loadBangumiTab() } }) {
                        Text("重试")
                    }
                }
            }
        } else {
            modules.forEachIndexed { moduleIndex, module ->
                if (module.items.isEmpty()) return@forEachIndexed
                
                if (module.title.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                            val headerIndex = moduleIndex * 10000 + 9999
                            val focusRequester = remember { FocusRequester() }
                            
                            LaunchedEffect(shouldRestoreFocus, modules.size) {
                                if (shouldRestoreFocus && headerIndex == initialFocusIndex) {
                                    Log.d("BiliTV_Focus", "BangumiTab: Requesting focus for Header $headerIndex")
                                    delay(100)
                                    focusRequester.requestFocus()
                                }
                            }
                            
                            ModuleHeader(
                                title = module.title,
                                onClick = {
                                    Log.d("BiliTV_Focus", "BangumiTab: Header clicked, setting restore=true")
                                    viewModel.shouldRestoreFocusToGrid = true
                                    when {
                                        module.title.contains("国创") -> onGuochuangHeaderClick()
                                        else -> onHeaderClick()
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            if (viewModel.shouldRestoreFocusToGrid && headerIndex == initialFocusIndex) {
                                                Log.d("BiliTV_Focus", "BangumiTab: Focus restored to Header $headerIndex")
                                                viewModel.shouldRestoreFocusToGrid = false
                                            }
                                            viewModel.updateFocusedIndex(TabType.BANGUMI, headerIndex)
                                        }
                                    }
                            )
                    }
                }
                
                module.items.forEachIndexed { itemIndex, item ->
                    item {
                            val video = Video(
                                id = item.season_id.toString(),
                                aid = 0,
                                bvid = "",
                                cid = 0,
                                title = item.title,
                                coverUrl = item.cover,
                                author = item.desc ?: "",
                                seasonId = item.season_id,
                                mediaId = item.oid,
                                seasonType = item.season_type,
                                badges = listOf(
                                    Badge(
                                        text = item.badge_info?.text ?: "",
                                        textColor = "",
                                        bgColor = item.badge_info?.bg_color ?: "",
                                        borderColor = item.badge_info?.bg_color ?: ""
                                    )
                                ),
                                bottomText = item.bottom_right_badge?.text,
                                followCount = item.stat?.follow ?: 0
                            )

                            val focusRequester = remember { FocusRequester() }
                            val currentIndex = moduleIndex * 10000 + itemIndex
                            
                            LaunchedEffect(shouldRestoreFocus, modules.size) {
                                if (shouldRestoreFocus && currentIndex == initialFocusIndex) {
                                    Log.d("BiliTV_Focus", "BangumiTab: Requesting focus for Item $currentIndex")
                                    delay(100)
                                    focusRequester.requestFocus()
                                }
                            }

                            VerticalMediaCard(
                                video = video,
                                onClick = {
                                    Log.d("BiliTV_Focus", "BangumiTab: Item clicked, setting restore=true")
                                    viewModel.shouldRestoreFocusToGrid = true
                                    onMediaClick(video)
                                },
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            if (viewModel.shouldRestoreFocusToGrid && currentIndex == initialFocusIndex) {
                                                Log.d("BiliTV_Focus", "BangumiTab: Focus restored to Item $currentIndex")
                                                viewModel.shouldRestoreFocusToGrid = false
                                            }
                                            viewModel.updateFocusedIndex(TabType.BANGUMI, currentIndex)
                                        }
                                    }
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaTabContent(
    viewModel: HomeViewModel,
    onMediaClick: (Video) -> Unit,
    onNavigateToCinemaList: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val modules = viewModel.cinemaTabModules
    val isLoading = viewModel.isCinemaTabLoading
    val error = viewModel.cinemaTabError
    val (initialIndex, initialOffset) = remember(viewModel.shouldRestoreFocusToGrid) { 
        val state = viewModel.getScrollState(TabType.CINEMA)
        Log.d("BiliTV_Focus", "CinemaTab: getScrollState index=${state.first} offset=${state.second} restore=${viewModel.shouldRestoreFocusToGrid}")
        state
    }
    // 使用带 key 的 remember，确保在需要恢复焦点时重新获取最新的焦点位置
    val initialFocusIndex = remember(viewModel.shouldRestoreFocusToGrid) { 
        val idx = viewModel.getFocusedIndex(TabType.CINEMA) 
        Log.d("BiliTV_Focus", "CinemaTab: getFocusedIndex index=$idx restore=${viewModel.shouldRestoreFocusToGrid}")
        idx
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )
    val shouldRestoreFocus = viewModel.shouldRestoreFocusToGrid

    // 记录是否是初次组合
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                // Log.d("BiliTV_Focus", "CinemaTab: Scroll update $idx $offset")
                viewModel.updateScrollState(TabType.CINEMA, idx, offset)
            }
    }

    LaunchedEffect(viewModel.refreshSignal) {
        if (isFirstComposition) {
            isFirstComposition = false
            Log.d("BiliTV_Focus", "CinemaTab: Initial composition, skipping refresh reset")
            return@LaunchedEffect
        }
        Log.d("BiliTV_Focus", "CinemaTab: Refresh signal received")
        gridState.scrollToItem(0)
        viewModel.updateScrollState(TabType.CINEMA, 0, 0)
    }

    LaunchedEffect(modules.size) {
        Log.d("BiliTV_Focus", "CinemaTab: Modules loaded size=${modules.size} initialIndex=$initialIndex")
        if (modules.isNotEmpty() && initialIndex > 0) {
             Log.d("BiliTV_Focus", "CinemaTab: Restoring scroll to $initialIndex")
             gridState.scrollToItem(initialIndex, initialOffset)
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp, top = 8.dp)
    ) {
        if (isLoading && modules.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (error != null && modules.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "加载失败：$error")
                    Button(onClick = { coroutineScope.launch { viewModel.loadCinemaTab() } }) {
                        Text("重试")
                    }
                }
            }
        } else {
            modules.forEachIndexed { moduleIndex, module ->
                if (module.items.isEmpty()) return@forEachIndexed
                
                if (module.title.isNotBlank()) {
                    val isClickableHeader = !module.title.contains("猜你喜欢")
                    item(span = { GridItemSpan(maxLineSpan) }) {
                            val headerIndex = moduleIndex * 10000 + 9999
                            val focusRequester = remember { FocusRequester() }
                            
                            LaunchedEffect(shouldRestoreFocus, modules.size) {
                                if (shouldRestoreFocus && headerIndex == initialFocusIndex) {
                                    Log.d("BiliTV_Focus", "CinemaTab: Requesting focus for Header $headerIndex")
                                    delay(100)
                                    focusRequester.requestFocus()
                                }
                            }

                            ModuleHeader(
                                title = module.title,
                                onClick = {
                                    Log.d("BiliTV_Focus", "CinemaTab: Header clicked, setting restore=true")
                                    viewModel.shouldRestoreFocusToGrid = true
                                    onNavigateToCinemaList(module.title) 
                                },
                                isFocusable = isClickableHeader,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            if (viewModel.shouldRestoreFocusToGrid && headerIndex == initialFocusIndex) {
                                                Log.d("BiliTV_Focus", "CinemaTab: Focus restored to Header $headerIndex")
                                                viewModel.shouldRestoreFocusToGrid = false
                                            }
                                            viewModel.updateFocusedIndex(TabType.CINEMA, headerIndex)
                                        }
                                    }
                            )
                    }
                }
                
                module.items.forEachIndexed { itemIndex, item ->
                    item {
                        val bottomText = item.index_show?.takeIf { it.isNotBlank() }
                            ?: item.new_ep?.index_show?.takeIf { it.isNotBlank() }
                            ?: item.bottom_right_badge?.text?.takeIf { it.isNotBlank() }
                        
                        val video = Video(
                            id = item.season_id.toString(),
                            aid = 0,
                            bvid = "",
                            cid = 0,
                            title = item.title,
                            coverUrl = item.cover,
                            author = item.desc ?: "",
                            seasonId = item.season_id,
                            mediaId = item.oid,
                            seasonType = item.season_type,
                            badges = listOf(
                                Badge(
                                    text = item.badge_info?.text ?: "",
                                    textColor = "",
                                    bgColor = item.badge_info?.bg_color ?: "",
                                    borderColor = item.badge_info?.bg_color ?: ""
                                )
                            ),
                            bottomText = bottomText,
                            followCount = item.stat?.follow ?: 0
                        )

                        val focusRequester = remember { FocusRequester() }
                        val currentIndex = moduleIndex * 10000 + itemIndex
                        
                        LaunchedEffect(shouldRestoreFocus, modules.size) {
                            if (shouldRestoreFocus && currentIndex == initialFocusIndex) {
                                Log.d("BiliTV_Focus", "CinemaTab: Requesting focus for Item $currentIndex")
                                delay(100)
                                focusRequester.requestFocus()
                            }
                        }

                        VerticalMediaCard(
                            video = video,
                            onClick = {
                                Log.d("BiliTV_Focus", "CinemaTab: Item clicked, setting restore=true")
                                viewModel.shouldRestoreFocusToGrid = true
                                onMediaClick(video)
                            },
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        if (viewModel.shouldRestoreFocusToGrid && currentIndex == initialFocusIndex) {
                                            Log.d("BiliTV_Focus", "CinemaTab: Focus restored to Item $currentIndex")
                                            viewModel.shouldRestoreFocusToGrid = false
                                        }
                                        viewModel.updateFocusedIndex(TabType.CINEMA, currentIndex)
                                    }
                                }
                        )
                    }
                }
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
            .padding(horizontal = 0.dp, vertical = 8.dp)
    ) {
        if (days.isNotEmpty()) {
            // 限制显示最近8个日期选项（最近更新 + 周一到周日）
            val displayDays = remember(days) {
                days.take(8)
            }
            val displayIndex = remember(selectedIndex) { 
                selectedIndex.coerceIn(0, (displayDays.size - 1).coerceAtLeast(0))
            }
            
            DateTabBarForTV(
                selectedIndex = displayIndex,
                onTabSelected = { newIndex ->
                    viewModel.timelineSelectedIndex = newIndex
                }
            )
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
                    contentPadding = PaddingValues(start = 0.dp, top = 4.dp, end = 0.dp, bottom = 4.dp)
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

@Composable
fun ModuleHeader(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFocusable: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .then(if (isFocusable) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
             Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                if (isFocusable) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "更多",
                        modifier = Modifier.size(16.dp),
                        tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}
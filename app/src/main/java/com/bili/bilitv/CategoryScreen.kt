package com.bili.bilitv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log

data class MainZone(
    val name: String,
    val tid: Int
)

// --- Data Models ---

@Serializable
data class CategoryListResponse(
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: CategoryListData? = null
)

@Serializable
data class CategoryListData(
    val type_list: List<CategoryItem>
)

@Serializable
data class CategoryItem(
    val id: Int,
    val name: String
)

@Serializable
data class CategoryVideoResponse(
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: CategoryVideoData? = null
)

@Serializable
data class CategoryVideoData(
    val archives: List<ArchiveItem>
)

@Serializable
data class ArchiveItem(
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val cover: String,
    val duration: Int,
    val pubdate: Long,
    val stat: ArchiveStat,
    val author: ArchiveAuthor
)

@Serializable
data class ArchiveStat(
    val view: Int,
    val like: Int,
    val danmaku: Int
)

@Serializable
data class ArchiveAuthor(
    val mid: Long,
    val name: String
)

// --- ViewModel ---

class CategoryViewModel : ViewModel(), VideoGridStateManager {
    private val _categories = MutableStateFlow<List<MainZone>>(emptyList())
    val categories: StateFlow<List<MainZone>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<MainZone?>(null)
    val selectedCategory: StateFlow<MainZone?> = _selectedCategory.asStateFlow()

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Scroll State Management
    private val scrollStates = mutableMapOf<Int, Pair<Int, Int>>() // tid -> (index, offset)
    private val focusedIndices = mutableMapOf<Int, Int>() // tid -> focusedIndex
    override var shouldRestoreFocusToGrid by mutableStateOf(false)

    private var currentPage = 1
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        _isLoading.value = true
        fetchCategories()
    }

    private fun fetchCategories() {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://member.bilibili.com/x/vupre/web/archive/human/type2/list")
                        .apply {
                            // 尝试添加 Cookie，如果已登录
                            SessionManager.getCookieString()?.let { cookie ->
                                addHeader("Cookie", cookie)
                            }
                        }
                        .build()
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val categoryListResponse = json.decodeFromString<CategoryListResponse>(body)
                        if (categoryListResponse.code == 0 && categoryListResponse.data != null) {
                            val zones = categoryListResponse.data.type_list.map {
                                MainZone(it.name, it.id)
                            }
                            _categories.value = zones
                            if (zones.isNotEmpty() && _selectedCategory.value == null) {
                                selectCategory(zones.first())
                            }
                        } else {
                             // API 返回非成功状态码 (e.g. -101 未登录) 或 data 为空，使用 fallback
                             Log.w("CategoryViewModel", "API error: code=${categoryListResponse.code}, message=${categoryListResponse.message}")
                             useFallbackCategories()
                        }
                    }
                } else {
                     Log.e("CategoryViewModel", "HTTP error: ${response.code}")
                     useFallbackCategories()
                }
            } catch (e: Exception) {
                Log.e("CategoryViewModel", "Error fetching categories", e)
                useFallbackCategories()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun useFallbackCategories() {
         val fallbackZones = listOf(
            MainZone("动画", 1), MainZone("番剧", 13), MainZone("国创", 167),
            MainZone("音乐", 3), MainZone("舞蹈", 129), MainZone("游戏", 4),
            MainZone("知识", 36), MainZone("科技", 188), MainZone("运动", 234),
            MainZone("汽车", 223), MainZone("生活", 160), MainZone("美食", 211),
            MainZone("动物圈", 217), MainZone("鬼畜", 119), MainZone("时尚", 155),
            MainZone("资讯", 5), MainZone("娱乐", 181), MainZone("影视", 181),
            MainZone("纪录片", 177), MainZone("电影", 23), MainZone("电视剧", 11)
        )
        _categories.value = fallbackZones
        if (_selectedCategory.value == null) {
            selectCategory(fallbackZones.first())
        }
    }

    fun selectCategory(zone: MainZone) {
        _selectedCategory.value = zone
        currentPage = 1 // Reset page logic. Note: The API uses `display_id` which increments.
                        // For simplicity, we assume fetchVideos handles pagination start.
        _videos.value = emptyList()
        _isLoading.value = true
        shouldRestoreFocusToGrid = false // Reset focus restore flag when changing category
        fetchVideos(zone.tid, isRefresh = true)
    }

    fun loadMore() {
        val currentZone = _selectedCategory.value ?: return
        fetchVideos(currentZone.tid, isRefresh = false)
    }

    private fun fetchVideos(regionId: Int, isRefresh: Boolean) {
        if (isRefresh) {
            currentPage = 1
        }

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val url = "https://api.bilibili.com/x/web-interface/region/feed/rcmd?" +
                            "display_id=$currentPage&request_cnt=15&from_region=$regionId&device=web&plat=30"
                    val request = Request.Builder()
                        .url(url)
                        .apply {
                            // 尝试添加 Cookie，如果已登录
                            SessionManager.getCookieString()?.let { cookie ->
                                addHeader("Cookie", cookie)
                            }
                            // 添加 Referer 防止被拦截
                            addHeader("Referer", "https://www.bilibili.com/")
                            addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        }
                        .build()
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val videoResponse = json.decodeFromString<CategoryVideoResponse>(body)
                        if (videoResponse.code == 0 && videoResponse.data != null) {
                            val newVideos = videoResponse.data.archives.map { archive ->
                                Video(
                                    id = archive.bvid,
                                    bvid = archive.bvid,
                                    cid = archive.cid,
                                    title = archive.title,
                                    coverUrl = archive.cover,
                                    author = archive.author.name,
                                    playCount = formatCount(archive.stat.view),
                                    danmakuCount = formatCount(archive.stat.danmaku),
                                    duration = formatDuration(archive.duration.toLong()),
                                    pubDate = archive.pubdate
                                )
                            }
                            if (isRefresh) {
                                _videos.value = newVideos
                            } else {
                                _videos.value += newVideos
                            }
                            currentPage++ 
                        } else {
                            Log.w("CategoryViewModel", "Video API returned error or empty data: code=${videoResponse.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CategoryViewModel", "Error fetching videos", e)
            } finally {
                if (isRefresh) _isLoading.value = false
            }
        }
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

    // VideoGridStateManager 接口实现
    override fun updateScrollState(key: Any, index: Int, offset: Int) {
        if (key is Int) {
            scrollStates[key] = index to offset
        }
    }

    override fun getScrollState(key: Any): Pair<Int, Int> {
        return if (key is Int) {
            scrollStates[key] ?: (0 to 0)
        } else {
            (0 to 0)
        }
    }

    override fun updateFocusedIndex(key: Any, index: Int) {
        if (key is Int) {
            focusedIndices[key] = index
        }
    }

    override fun getFocusedIndex(key: Any): Int {
        return if (key is Int) {
            focusedIndices[key] ?: -1
        } else {
            -1
        }
    }
    
    fun onEnterFullScreen() {
        shouldRestoreFocusToGrid = true
    }
}

// --- UI Components ---

@Composable
fun CategoryScreen(
    viewModel: CategoryViewModel = viewModel(),
    onEnterFullScreen: (VideoPlayInfo, String) -> Unit = { _, _ -> }
) {
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }
    
    // 进入动画
    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {

    // 处理视频点击
    val handleVideoClick: (Video) -> Unit = { video ->
        if (video.bvid.isNotEmpty() && video.cid != 0L) {
            Log.d("BiliTV", "Video clicked: ${video.title} (bvid=${video.bvid}, cid=${video.cid})")
            coroutineScope.launch {
                val playInfo = VideoPlayUrlFetcher.fetchPlayUrl(
                    bvid = video.bvid,
                    cid = video.cid,
                    qn = 80, // 1080P - 非大会员最高清晰度
                    fnval = 4048, // DASH格式
                    cookie = SessionManager.getCookieString()
                )
                
                if (playInfo != null) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp) // 统一顶部间距
    ) {
        // 横向滚动Tabs
        if (categories.isNotEmpty()) {
            CommonTabRow(
                tabs = categories.map { zone -> TabItem(zone.tid, zone.name) },
                selectedTab = selectedCategory?.tid ?: 0,
                onTabSelected = { tid ->
                    val selectedZone = categories.find { it.tid == tid }
                    selectedZone?.let { viewModel.selectCategory(it) }
                },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp)
            )
        }

        // 内容区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && videos.isEmpty()) {
                CircularProgressIndicator()
            } else {
                // 使用 key 确保切换 Tab 时重新创建 Grid，从而应用新的初始滚动位置
                key(selectedCategory?.tid) {
                    CommonVideoGrid(
                        videos = videos,
                        stateManager = viewModel,
                        stateKey = selectedCategory?.tid ?: 0,
                        columns = 4,
                        onVideoClick = handleVideoClick,
                        onLoadMore = { viewModel.loadMore() },
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



package com.bili.bilitv

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
    TRENDING("动态"),
    HOT("热门")
}

/**
 * 首页屏幕
 * 包含三个Tab和视频列表
 */
@OptIn(ExperimentalSerializationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onEnterFullScreen: (VideoPlayInfo, String) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableStateOf(TabType.RECOMMEND) }
    var hotVideos by remember { mutableStateOf<List<VideoItemData>>(emptyList()) }
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

    LaunchedEffect(selectedTab) {
        if (selectedTab == TabType.HOT) {
            Log.d("BiliTV", "Fetching popular videos for Hot tab...")
            try {
                val url = "https://api.bilibili.com/x/web-interface/popular?pn=1&ps=20"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36")
                
                // 添加Cookie信息（如果已登录）
                val cookieString = SessionManager.getCookieString()
                if (cookieString != null) {
                    requestBuilder.header("Cookie", cookieString)
                    Log.d("BiliTV", "Added Cookie to request: $cookieString")
                } else {
                    Log.d("BiliTV", "No session found, requesting without Cookie")
                }
                
                val request = requestBuilder.build()
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }

                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        Log.d("BiliTV", "Popular Videos API Response: $responseBody")
                        val popularResponse = withContext(Dispatchers.Default) {
                            json.decodeFromString<PopularVideoResponse>(responseBody)
                        }
                        if (popularResponse.code == 0 && popularResponse.data != null) {
                            hotVideos = popularResponse.data.list
                            Log.d("BiliTV", "Fetched ${hotVideos.size} popular videos.")
                        } else {
                            Log.e("BiliTV", "Popular Videos API error: ${popularResponse.message}")
                        }
                    }
                } else {
                    Log.e("BiliTV", "Popular Videos HTTP error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("BiliTV", "Popular Videos Network error: ${e.localizedMessage}")
            }
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
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 视频列表
            val videosToDisplay = remember(selectedTab, hotVideos) {
                when (selectedTab) {
                    TabType.HOT -> hotVideos.map { mapVideoItemDataToVideo(it) }
                    else -> getVideosForTab(selectedTab)
                }
            }
            VideoGrid(
                videos = videosToDisplay,
                onVideoClick = handleVideoClick
            )
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
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
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
        items(videos) { video ->
            VideoItem(
                video = video,
                onClick = onVideoClick
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
        TabType.TRENDING -> "动态视频"
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

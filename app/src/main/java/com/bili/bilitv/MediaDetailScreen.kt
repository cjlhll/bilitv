package com.bili.bilitv

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import okhttp3.FormBody
import java.net.URLEncoder

suspend fun toggleFollow(
    seasonId: Long,
    currentFollowed: Boolean,
    sessdata: String,
    csrf: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val url = if (currentFollowed) {
                "https://api.bilibili.com/pgc/web/follow/del"
            } else {
                "https://api.bilibili.com/pgc/web/follow/add"
            }
            
            Log.d("FollowAction", "开始${if (currentFollowed) "取消追番" else "追番"} seasonId=$seasonId")
            
            val encodedSessdata = URLEncoder.encode(sessdata, "UTF-8")
            
            val formBody = FormBody.Builder()
                .add("season_id", seasonId.toString())
                .add("csrf", csrf)
                .build()
            
            Log.d("FollowAction", "请求URL: $url")
            Log.d("FollowAction", "请求参数: season_id=$seasonId, csrf=$csrf")
            Log.d("FollowAction", "原始SESSDATA: ${sessdata.take(50)}...")
            Log.d("FollowAction", "编码后SESSDATA: ${encodedSessdata.take(50)}...")
            
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("Cookie", "SESSDATA=$encodedSessdata")
                .addHeader("Referer", "https://www.bilibili.com/anime/?spm_id_from=333.1007.0.0")
                .build()
            
            val client = OkHttpClient()
            client.newCall(request).execute().use { response ->
                Log.d("FollowAction", "响应状态码: ${response.code}")
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Log.d("FollowAction", "响应内容: $body")
                    
                    if (body != null) {
                        val json = Json { ignoreUnknownKeys = true }
                        val result = json.decodeFromString<FollowResponse>(body)
                        Log.d("FollowAction", "解析结果: code=${result.code}, message=${result.message}")
                        
                        val success = result.code == 0
                        Log.d("FollowAction", "操作${if (success) "成功" else "失败"}")
                        return@use success
                    }
                }
                Log.d("FollowAction", "请求失败或响应体为空")
                false
            }
        } catch (e: Exception) {
            Log.e("FollowAction", "异常: ${e.message}", e)
            false
        }
    }
}

@Serializable
data class FollowResponse(
    val code: Int,
    val message: String = ""
)

@Composable
fun MediaDetailScreen(
    media: Video,
    viewModel: MediaDetailViewModel,
    onBack: () -> Unit,
    onPlay: (VideoPlayInfo, String) -> Unit = { _, _ -> },
    onNavigateToMedia: (Video) -> Unit = {}
) {
    // 使用媒体ID作为key，确保每次切换媒体时重新创建组件
    val mediaKey = "${media.seasonId}_${media.mediaId}"
    key(mediaKey) {
    val logTag = "MediaDetail"
    val context = LocalContext.current
    
    // Initial data load
    LaunchedEffect(mediaKey) {
        viewModel.loadMedia(media)
    }

    val isStateReady = viewModel.currentMediaId == mediaKey
    if (!isStateReady) {
        Box(modifier = Modifier.fillMaxSize())
    } else {
    // Scroll state persistence
    val scrollState = rememberScrollState(initial = viewModel.scrollPosition)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { pos ->
            viewModel.scrollPosition = pos
        }
    }

    val detailMedia = viewModel.detailMedia ?: media
    var isAscending by remember { mutableStateOf(viewModel.isAscending) } // Use local state initialized from VM, or bind directly?
    // Binding directly to VM property for persistence
    // But isAscending in VM should be mutable.
    
    // Sync isAscending changes to ViewModel
    LaunchedEffect(isAscending) {
        viewModel.isAscending = isAscending
    }

    val isDetailLoading = viewModel.isDetailLoading
    val detailError = viewModel.detailError
    val sections = viewModel.sections

    val coverModel = remember(detailMedia.coverUrl) {
        ImageRequest.Builder(context)
            .data(detailMedia.coverUrl)
            .size(ImageConfig.VIDEO_COVER_SIZE)
            .build()
    }
    
    val displayedMainEpisodes = remember(sections, isAscending) {
        val main = (sections.firstOrNull() as? EpisodeCardSection)?.episodes.orEmpty()
        if (isAscending) main else main.reversed()
    }

    val primaryEpisode = displayedMainEpisodes.firstOrNull()

    // Focus requesters
    val focusRequester = remember { FocusRequester() }
    val playButtonRequester = remember { FocusRequester() }
    val followButtonRequester = remember { FocusRequester() }

    // Focus restoration - managed by ViewModel state
    val shouldRestoreFocus = viewModel.shouldRestoreFocus

    // Restore button focus if no episode focus is set
    LaunchedEffect(shouldRestoreFocus, viewModel.lastFocusedButton) {
        if (shouldRestoreFocus && viewModel.lastFocusedId == null && viewModel.lastFocusedButton != null) {
            kotlinx.coroutines.delay(100)
            when (viewModel.lastFocusedButton) {
                "play" -> playButtonRequester.requestFocus()
                "follow" -> followButtonRequester.requestFocus()
            }
            viewModel.shouldRestoreFocus = false
        }
    }

    // Auto-focus on primary episode if no other focus recorded
    // Or just rely on EpisodeCard logic
    
    BackHandler {
        viewModel.clearState()
        onBack()
    }
    
    val coroutineScope = rememberCoroutineScope()
    fun handlePlay(episode: MediaEpisode) {
        viewModel.shouldRestoreFocus = true
        coroutineScope.launch {
            val cookie = SessionManager.getSession()?.toCookieString()
            // 使用ep_id获取播放地址
            val playInfo = VideoPlayUrlFetcher.fetchPgcPlayUrl(
                epId = episode.id,
                cid = episode.cid,
                bvid = episode.bvid,
                aid = episode.aid,
                cookie = cookie
            )
            
            if (playInfo != null) {
                onPlay(playInfo, "${episode.indexTitle} ${episode.longTitle}")
            } else {
                // 如果获取失败，尝试打开网页
                val targetUrl = episode.url.ifBlank { detailMedia.url }
                if (targetUrl.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AsyncImage(
            model = coverModel,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.25f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState)
        ) {
            BoxWithConstraints {
                val infoHeight = (this@BoxWithConstraints.maxHeight * 0.4f).coerceIn(100.dp, 240.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(infoHeight)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 6.dp
                    ) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = detailMedia.title,
                            modifier = Modifier
                                .height(infoHeight)
                                .aspectRatio(3f / 4f),
                            contentScale = ContentScale.Crop
                        )
                    }

                    val infoWidth = (this@BoxWithConstraints.maxWidth - 238.dp).coerceAtLeast(0.dp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .width(infoWidth)
                            .height(infoHeight)
                    ) {
                        Text(
                            text = detailMedia.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = buildString {
                                if (detailMedia.epSize > 0) append("全${detailMedia.epSize}话")
                                if (detailMedia.mediaScore > 0) append(" | 评分 ${String.format("%.1f", detailMedia.mediaScore)}")
                                if (detailMedia.mediaScoreUsers > 0) append("（${detailMedia.mediaScoreUsers}人评分）")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Text(
                            text = detailMedia.desc.ifBlank { detailMedia.staff },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    if (primaryEpisode != null) {
                                        handlePlay(primaryEpisode)
                                    } else {
                                        val targetUrl = primaryEpisode?.url?.ifBlank { media.url } ?: media.url
                                        if (targetUrl.isNotBlank()) {
                                            viewModel.shouldRestoreFocus = true
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .focusRequester(playButtonRequester)
                                    .onFocusChanged {
                                        isPlayFocused = it.isFocused
                                        if (it.isFocused) viewModel.lastFocusedButton = "play"
                                    },
                                border = if (isPlayFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary) else null
                            ) {
                                Text(text = media.buttonText.ifBlank { "开始观看" }, fontSize = 16.sp)
                            }

                            var isFollowFocused by remember { mutableStateOf(false) }
                            var isFollowed by remember(detailMedia.seasonId) { mutableStateOf(detailMedia.isFollow) }
                            var isFollowLoading by remember { mutableStateOf(false) }
                            
                            Button(
                                onClick = {
                                    Log.d("FollowButton", "按钮被点击 seasonId=${detailMedia.seasonId} isFollowed=$isFollowed isLoading=$isFollowLoading")
                                    
                                    if (!isFollowLoading && detailMedia.seasonId > 0) {
                                        isFollowLoading = true
                                        Log.d("FollowButton", "开始处理追番操作")
                                        
                                        coroutineScope.launch {
                                            val session = SessionManager.getSession()
                                            Log.d("FollowButton", "获取Session: ${if (session != null) "成功" else "失败"}")
                                            
                                            if (session != null) {
                                                Log.d("FollowButton", "SESSDATA: ${session.sessdata.take(50)}...")
                                                
                                                val result = toggleFollow(
                                                    seasonId = detailMedia.seasonId,
                                                    currentFollowed = isFollowed,
                                                    sessdata = session.sessdata,
                                                    csrf = session.biliJct
                                                )
                                                
                                                Log.d("FollowButton", "toggleFollow返回结果: $result")
                                                
                                                if (result) {
                                                    isFollowed = !isFollowed
                                                    Log.d("FollowButton", "状态已更新: isFollowed=$isFollowed")
                                                } else {
                                                    Log.d("FollowButton", "状态未更新，接口返回失败")
                                                }
                                            } else {
                                                Log.w("FollowButton", "未登录，无法追番")
                                            }
                                            isFollowLoading = false
                                            Log.d("FollowButton", "追番操作完成")
                                        }
                                    } else {
                                        Log.d("FollowButton", "无法执行: isLoading=$isFollowLoading seasonId=${detailMedia.seasonId}")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .focusRequester(followButtonRequester)
                                    .onFocusChanged {
                                        isFollowFocused = it.isFocused
                                        if (it.isFocused) viewModel.lastFocusedButton = "follow"
                                    },
                                border = if (isFollowFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                                enabled = !isFollowLoading
                            ) {
                                val followText = if (detailMedia.mediaType == 1) "追番" else "追剧"
                                Text(text = if (isFollowed) "已$followText" else followText, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            sections.forEachIndexed { index, section ->
                when (section) {
                    is EpisodeCardSection -> {
                        val rowKey = "episodes_$index"
                        var resetToStartSignal by remember(rowKey) { mutableStateOf(0) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = section.title.ifBlank { if (section.isMain) "正片剧集" else "其他分区" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )

                            if (section.isMain) {
                                var isSortFocused by remember { mutableStateOf(false) }
                                TextButton(
                                    onClick = {
                                        isAscending = !isAscending
                                        viewModel.rowScrollStates[rowKey] = 0 to 0
                                        resetToStartSignal += 1
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .focusProperties {
                                            canFocus = !(shouldRestoreFocus && viewModel.lastFocusedId != null)
                                        }
                                        .onFocusChanged { isSortFocused = it.isFocused },
                                    border = if (isSortFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Icon(
                                        imageVector = if (isAscending) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAscending) "正序" else "倒序",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        val episodesToShow = if (section.isMain) displayedMainEpisodes else section.episodes
                        if (episodesToShow.isEmpty()) {
                            Text(
                                text = "暂无剧集信息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            val rowResetKey = if (section.isMain) resetToStartSignal else 0
                            key(rowKey, rowResetKey) {
                                val savedRowState = viewModel.rowScrollStates[rowKey] ?: (0 to 0)
                                val rowState = rememberLazyListState(
                                    initialFirstVisibleItemIndex = savedRowState.first,
                                    initialFirstVisibleItemScrollOffset = savedRowState.second
                                )
                                
                                LaunchedEffect(rowState, rowKey) {
                                    snapshotFlow { 
                                        rowState.firstVisibleItemIndex to rowState.firstVisibleItemScrollOffset 
                                    }.collect { (index, offset) ->
                                        viewModel.rowScrollStates[rowKey] = index to offset
                                    }
                                }
                                
                                LazyRow(
                                    state = rowState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                ) {
                                    itemsIndexed(
                                        items = episodesToShow,
                                        key = { _, ep -> ep.id }
                                    ) { itemIndex, ep ->
                                        val focusRequester = remember { FocusRequester() }
                                        val isTarget = shouldRestoreFocus && (viewModel.lastFocusedId == ep.id.toString())
    
                                        LaunchedEffect(shouldRestoreFocus, episodesToShow.size, viewModel.lastFocusedId) {
                                            if (isTarget) {
                                                kotlinx.coroutines.delay(50)
                                                focusRequester.requestFocus()
                                            }
                                        }
    
                                        EpisodeCard(
                                            episode = ep,
                                            onClick = { handlePlay(ep) },
                                            modifier = Modifier.width(240.dp),
                                            focusRequester = focusRequester,
                                            onFocus = {
                                                viewModel.lastFocusedId = ep.id.toString()
                                                if (viewModel.shouldRestoreFocus && viewModel.lastFocusedId == ep.id.toString()) {
                                                    viewModel.shouldRestoreFocus = false
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }
                    is SeasonCardSection -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = section.title.ifBlank { "系列/多季" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (section.seasons.isEmpty()) {
                            Text(
                                text = "暂无剧集信息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            val rowKey = "seasons_$index"
                            val savedRowState = viewModel.rowScrollStates[rowKey] ?: (0 to 0)
                            val rowState = rememberLazyListState(
                                initialFirstVisibleItemIndex = savedRowState.first,
                                initialFirstVisibleItemScrollOffset = savedRowState.second
                            )
                            
                            LaunchedEffect(rowState, rowKey) {
                                snapshotFlow { 
                                    rowState.firstVisibleItemIndex to rowState.firstVisibleItemScrollOffset 
                                }.collect { (index, offset) ->
                                    viewModel.rowScrollStates[rowKey] = index to offset
                                }
                            }
                            
                            LazyRow(
                                state = rowState,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    items = section.seasons,
                                    key = { _, season -> season.seasonId }
                                ) { itemIndex, season ->
                                    val focusRequester = remember { FocusRequester() }
                                    val isTarget = shouldRestoreFocus && (viewModel.lastFocusedId == season.seasonId.toString())

                                    LaunchedEffect(shouldRestoreFocus, section.seasons.size, viewModel.lastFocusedId) {
                                        if (isTarget) {
                                            kotlinx.coroutines.delay(50)
                                            focusRequester.requestFocus()
                                        }
                                    }

                                    VerticalMediaCard(
                                        video = season,
                                        onClick = {
                                            viewModel.shouldRestoreFocus = true
                                            onNavigateToMedia(season)
                                        },
                                        modifier = Modifier
                                            .width(156.dp)
                                            .focusRequester(focusRequester)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    viewModel.lastFocusedId = season.seasonId.toString()
                                                    if (viewModel.shouldRestoreFocus && viewModel.lastFocusedId == season.seasonId.toString()) {
                                                        viewModel.shouldRestoreFocus = false
                                                    }
                                                }
                                            }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
            
            when {
                isDetailLoading -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "正在加载详情…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                detailError != null -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = detailError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    }
    } // key(mediaKey)
}

@Serializable
data class SeasonSimpleResponse(
    val code: Int,
    val message: String = "",
    val result: SeasonSimpleResult? = null
)

@Serializable
data class SeasonSimpleResult(
    val seasons: List<SeasonSimpleItem>? = null
)

@Serializable
data class SeasonSimpleItem(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("season_title") val seasonTitle: String = "",
    val cover: String = "",
    @SerialName("horizontal_cover_169") val horizontalCover169: String = "",
    @SerialName("horizontal_cover_1610") val horizontalCover1610: String = "",
    val badge: String = "",
    @SerialName("badge_info") val badgeInfo: SeasonBadgeInfo? = null,
    @SerialName("new_ep") val newEp: SeasonNewEp? = null,
    val link: String = ""
)

@Serializable
data class SeasonBadgeInfo(
    val text: String = "",
    @SerialName("bg_color") val bgColor: String = "",
    @SerialName("text_color") val textColor: String = ""
)

@Serializable
data class SeasonNewEp(
    @SerialName("index_show") val indexShow: String = ""
)

@Serializable
data class MediaBasicResponse(
    val code: Int,
    val message: String = "",
    val result: MediaBasicResult? = null
)

@Serializable
data class MediaBasicResult(
    val media: MediaBasicInfo? = null
)

@Serializable
data class MediaBasicInfo(
    @SerialName("media_id") val mediaId: Long = 0,
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("horizontal_picture") val horizontalPicture: String = "",
    @SerialName("share_url") val shareUrl: String = "",
    val rating: MediaRating? = null,
    @SerialName("new_ep") val newEp: MediaNewEp? = null,
    val areas: List<MediaArea> = emptyList(),
    val type: Int = 0,
    @SerialName("type_name") val typeName: String = ""
)

@Serializable
data class MediaRating(
    val score: Double = 0.0,
    val count: Int = 0
)

@Serializable
data class MediaNewEp(
    @SerialName("index_show") val indexShow: String = ""
)

@Serializable
data class MediaArea(
    val id: Int = 0,
    val name: String = ""
)

sealed interface DetailSection {
    val title: String
}

data class EpisodeCardSection(
    override val title: String = "",
    val type: Int = 0,
    val episodes: List<MediaEpisode> = emptyList(),
    val isMain: Boolean = false
) : DetailSection

data class SeasonCardSection(
    override val title: String = "",
    val seasons: List<Video> = emptyList()
) : DetailSection

enum class CardStyle { Video16x10, Cover3x4 }

@Serializable
data class SeasonDetailResponse(
    val code: Int,
    val message: String = "",
    val result: SeasonDetailResult? = null
)

@Serializable
data class SeasonDetailResult(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("media_id") val mediaId: Long = 0,
    val title: String = "",
    @SerialName("season_title") val seasonTitle: String = "",
    val cover: String = "",
    @SerialName("horizontal_cover_169") val horizontalCover169: String = "",
    @SerialName("horizontal_cover_1610") val horizontalCover1610: String = "",
    val evaluate: String = "",
    val rating: MediaRating? = null,
    @SerialName("new_ep") val newEp: SeasonNewEp? = null,
    val episodes: List<SeasonEpisode> = emptyList(),
    @SerialName("section") val section: List<SeasonSection> = emptyList(),
    @SerialName("sections") val sections: List<SeasonSection> = emptyList(),
    val seasons: List<SeasonBrief> = emptyList(),
    val series: SeasonSeries? = null,
    val type: Int = 0,
    @SerialName("type_name") val typeName: String = "",
    val link: String = "",
    val staff: String = "",
    val cv: String = "",
    val bvid: String = ""
)

@Serializable
data class SeasonEpisode(
    val id: Long = 0,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    @SerialName("long_title") val longTitle: String = "",
    val cover: String = "",
    @SerialName("share_url") val shareUrl: String = "",
    val badge: String = "",
    @SerialName("badge_info") val badgeInfo: SeasonBadgeInfo? = null,
    val bvid: String = ""
)

@Serializable
data class SeasonSection(
    val episodes: List<SeasonEpisode> = emptyList(),
    val title: String = "",
    val type: Int = 0
)

@Serializable
data class SeasonBrief(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("season_title") val seasonTitle: String = "",
    val cover: String = "",
    @SerialName("horizontal_cover_169") val horizontalCover169: String = "",
    @SerialName("horizontal_cover_1610") val horizontalCover1610: String = "",
    val badge: String = "",
    @SerialName("badge_info") val badgeInfo: SeasonBadgeInfo? = null,
    @SerialName("new_ep") val newEp: SeasonNewEp? = null,
    val link: String = ""
) 

@Serializable
data class SeasonSeries(
    @SerialName("series_id") val seriesId: Long = 0,
    val title: String = "",
    val seasons: List<SeasonBrief> = emptyList()
)


@Composable
private fun EpisodeCard(
    episode: MediaEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = focusRequester ?: remember { FocusRequester() }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .focusRequester(internalFocusRequester)
            .onFocusChanged { 
                isFocused = it.isFocused 
                if (it.isFocused) onFocus()
            },
        border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = onClick
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = episode.cover,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Bottom Shadow with Episode Number
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = episode.indexTitle.ifBlank { episode.title },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }

                if (episode.badges.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        episode.badges.take(2).forEach { badge ->
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = episode.longTitle.ifBlank { "${episode.indexTitle} ${episode.title}" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


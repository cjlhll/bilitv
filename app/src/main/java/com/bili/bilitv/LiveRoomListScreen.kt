package com.bili.bilitv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bili.bilitv.utils.WbiUtil
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

// --- Data Models ---

@Serializable
data class LiveRoomListResponse(
    val code: Int,
    val message: String,
    val data: List<LiveRoomItem> = emptyList()
)

@Serializable
data class LiveRoomItem(
    val roomid: Int,
    val uid: Long,
    val title: String,
    val uname: String,
    val online: Int,
    val user_cover: String,
    val system_cover: String,
    val cover: String,
    val face: String,
    val parent_id: Int = 0,
    val parent_name: String = "",
    val area_id: Int = 0,
    val area_name: String = ""
)

// --- ViewModel ---

class LiveRoomListViewModel : ViewModel(), VideoGridStateManager {
    private val _rooms = MutableStateFlow<List<LiveRoomItem>>(emptyList())
    val rooms: StateFlow<List<LiveRoomItem>> = _rooms.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    // Pagination & State
    private var currentPage = 1
    private var isLastPage = false
    private var currentParentAreaId: String? = null
    private var currentAreaId: String? = null
    
    var scrollIndex = 0
    var scrollOffset = 0
    var focusedIndex = -1
    
    // 焦点恢复标志：首次进入或从播放器返回时恢复焦点
    override var shouldRestoreFocusToGrid by mutableStateOf(false)

    // VideoGridStateManager 接口实现
    override fun updateScrollState(key: Any, index: Int, offset: Int) {
        scrollIndex = index
        scrollOffset = offset
    }

    override fun getScrollState(key: Any): Pair<Int, Int> {
        return scrollIndex to scrollOffset
    }

    override fun updateFocusedIndex(key: Any, index: Int) {
        focusedIndex = index
    }

    override fun getFocusedIndex(key: Any): Int {
        return focusedIndex
    }

    /**
     * 进入直播间
     */
    fun enterLiveRoom(roomId: Int, title: String, uname: String, onEnterLiveRoom: (LivePlayInfo, String) -> Unit) {
        viewModelScope.launch {
            val livePlayInfo = LiveStreamUrlFetcher.fetchLivePlayInfo(roomId, title, uname)
            if (livePlayInfo != null) {
                // 进入播放器时，标记需要恢复焦点（用于返回时）
                shouldRestoreFocusToGrid = true
                onEnterLiveRoom(livePlayInfo, title)
            } else {
                Log.e("LiveRoomListViewModel", "无法获取直播间播放信息")
            }
        }
    }

    fun initialLoad(parentAreaId: String, areaId: String, force: Boolean = false) {
        if (!force && currentAreaId == areaId && _rooms.value.isNotEmpty()) {
            // Already loaded, don't reset - 从播放器返回时保持原有位置
            // 但需要标记恢复焦点
            shouldRestoreFocusToGrid = true
            return
        }
        
        currentParentAreaId = parentAreaId
        currentAreaId = areaId
        currentPage = 1
        isLastPage = false
        // 只有当是新区域时才重置滚动和焦点位置
        scrollIndex = 0
        scrollOffset = 0
        focusedIndex = -1
        _rooms.value = emptyList()
        _isLoading.value = true
        // 首次进入时，设置焦点恢复标志为true，确保焦点定位到第一个视频卡片
        shouldRestoreFocusToGrid = true
        loadData()
    }

    /**
     * 重置滚动位置和焦点（在退出页面时调用）
     */
    fun resetScrollAndFocus() {
        scrollIndex = 0
        scrollOffset = 0
        focusedIndex = -1
        shouldRestoreFocusToGrid = false
    }

    fun loadMore() {
        if (_isLoading.value || isLastPage || currentAreaId == null) return
        currentPage++
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val params = mutableMapOf(
                    "parent_area_id" to (currentParentAreaId ?: "0"),
                    "area_id" to (currentAreaId ?: "0"),
                    "sort_type" to "online",
                    "page" to currentPage.toString(),
                    "page_size" to "30"
                )

                // Build query string
                val queryBuilder = StringBuilder()
                params.forEach { (key, value) ->
                    if (queryBuilder.isNotEmpty()) {
                        queryBuilder.append("&")
                    }
                    queryBuilder.append(WbiUtil.encodeURIComponent(key))
                    queryBuilder.append("=")
                    queryBuilder.append(WbiUtil.encodeURIComponent(value))
                }
                val query = queryBuilder.toString()
                
                val url = "https://api.live.bilibili.com/room/v1/Area/getRoomList?$query"
                Log.d("LiveRoomListViewModel", "Request URL: $url")

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                
                SessionManager.getCookieString()?.let {
                    requestBuilder.header("Cookie", it)
                }
                    
                val request = requestBuilder.build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        try {
                            val apiResp = json.decodeFromString<LiveRoomListResponse>(body)
                            if (apiResp.code == 0) {
                                val newItems = apiResp.data
                                if (newItems.isEmpty()) {
                                    isLastPage = true
                                } else {
                                    _rooms.value = _rooms.value + newItems
                                }
                            } else {
                                Log.e("LiveRoomListViewModel", "API Error: ${apiResp.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("LiveRoomListViewModel", "Parsing Error", e)
                        }
                    }
                } else {
                    Log.e("LiveRoomListViewModel", "HTTP Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("LiveRoomListViewModel", "Exception loading rooms", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// --- Screen ---

@Composable
fun LiveRoomListScreen(
    area: LiveAreaItem,
    enterTimestamp: Long,
    onBack: () -> Unit,
    onEnterFullScreen: (VideoPlayInfo, String) -> Unit,
    onEnterLiveRoom: (LivePlayInfo, String) -> Unit,
    viewModel: LiveRoomListViewModel = viewModel()
) {
    val rooms by viewModel.rooms.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var isVisible by remember { mutableStateOf(false) }
    
    // 进入动画
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // Handle hardware/remote back button
    BackHandler {
        // 返回直播分区时，重置直播列表的状态
        viewModel.resetScrollAndFocus()
        isVisible = false
        // 延迟回调,等待动画完成
        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(200)
            onBack()
        }
    }

    LaunchedEffect(enterTimestamp) {
        // 从播放器返回时不强制重新加载,保持滚动和焦点位置
        viewModel.initialLoad(area.parent_id, area.id, force = false)
    }

    // 只使用淡入淡出动画
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp) // 统一顶部间距
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 12.dp, bottom = 8.dp) // 与列表项保持相同的左侧间距
        ) {
            Text(
                text = area.name,
                style = MaterialTheme.typography.headlineSmall // 减小字体大小
            )
        }

        // Grid
        if (rooms.isEmpty()) {
            // Focus Trap: Ensure focus stays within this screen while loading
            val loadingFocusRequester = remember { FocusRequester() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(loadingFocusRequester)
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    // Empty state
                    Text("没有找到直播间", style = MaterialTheme.typography.bodyLarge)
                }
                
                // Request focus immediately when this Box appears
                LaunchedEffect(Unit) {
                    loadingFocusRequester.requestFocus()
                }
            }
        } else {
            CommonVideoGrid(
                items = rooms,
                stateManager = viewModel,
                stateKey = "live_room_${area.id}",
                columns = 4,
                onItemClick = { room ->
                    Log.d("LiveRoomList", "Clicked room: ${room.title}")
                    // 获取直播流信息并跳转到播放页
                    viewModel.enterLiveRoom(room.roomid, room.title, room.uname, onEnterLiveRoom)
                },
                onLoadMore = { viewModel.loadMore() },
                horizontalSpacing = 12.dp,
                verticalSpacing = 12.dp,
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp, start = 12.dp, end = 12.dp) // 统一顶部间距
            ) { room, itemModifier ->
                LiveRoomCard(
                    room = room,
                    onClick = {
                        Log.d("LiveRoomList", "Clicked room: ${room.title}")
                        viewModel.enterLiveRoom(room.roomid, room.title, room.uname, onEnterLiveRoom)
                    },
                    modifier = itemModifier
                )
            }
        }
    }
    }
}

@Composable
fun LiveRoomCard(
    room: LiveRoomItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isFocused) 1f else 0f),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isFocused) 8.dp else 2.dp
            ),
            border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
        ) {
            Column {
                // Cover Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp) // Match VideoItem height
                        .background(Color.LightGray)
                ) {
                    AsyncImage(
                        model = room.cover,
                        contentDescription = room.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Online count badge
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = formatOnline(room.online),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                
                // Title
                Text(
                    text = room.title,
                    style = MaterialTheme.typography.bodyMedium,
                    minLines = 1, // Corrected to match maxLines=1
                    maxLines = 1, // Requirement: 1 line with ellipsis
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Author
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = room.uname,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun formatOnline(online: Int): String {
    return if (online >= 10000) {
        String.format("%.1f万", online / 10000f)
    } else {
        online.toString()
    }
}
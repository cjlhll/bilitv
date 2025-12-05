package com.bili.bilitv

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bili.bilitv.utils.QRCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext

@Serializable
data class UserInfoResponse(
    val code: Int,
    val message: String,
    val data: UserInfoData? = null
)

@Serializable
data class UserInfoData(
    val mid: Long,
    val uname: String,
    val face: String,
    val isLogin: Boolean = false,
    val wbi_img: WbiImg? = null
)

@Serializable
data class QrCodeResponse(
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: QrCodeData
)

@Serializable
data class QrCodeData(
    val url: String,
    val qrcode_key: String
)

@Serializable
data class QrCodePollResponse(
    val code: Int,
    val message: String,
    val data: QrCodePollData? // Nullable if data might not be present on all status codes
)

@Serializable
data class QrCodePollData(
    val url: String? = null, // May not always be present
    val refresh_token: String? = null,
    val timestamp: Long? = null, // Timestamp might be nullable if poll fails
    val code: Int? = null // This 'code' is the status code, e.g., 0 for success, 86038 for expired
)

@Serializable
data class LoggedInSession(
    val dedeUserID: String,
    val dedeUserIDCkMd5: String,
    val sessdata: String,
    val biliJct: String,
    val expires: Long, // Unix timestamp
    val refreshToken: String,
    val crossDomainUrl: String
) {
    /**
     * 生成Cookie字符串，用于HTTP请求头
     */
    fun toCookieString(): String {
        return "DedeUserID=$dedeUserID; DedeUserID__ckMd5=$dedeUserIDCkMd5; SESSDATA=$sessdata; bili_jct=$biliJct"
    }
}

/**
 * 全局登录会话管理器 - 支持内存和本地存储
 */
object SessionManager {
    private var currentSession: LoggedInSession? = null
    private var context: Context? = null
    private const val PREFS_NAME = "bili_session"
    private const val SESSION_KEY = "logged_in_session"

    var wbiImgKey: String? = null
    var wbiSubKey: String? = null
    
    fun init(context: Context) {
        this.context = context
        // 应用启动时从SharedPreferences恢复登录状态
        loadSessionFromStorage()
    }
    
    fun setSession(session: LoggedInSession?) {
        currentSession = session
        Log.d("BiliTV", "Session updated: ${session?.dedeUserID ?: "null"}")
        // 保存到SharedPreferences
        if (session != null) {
            saveSessionToStorage(session)
        } else {
            clearSessionFromStorage()
        }
    }
    
    fun getSession(): LoggedInSession? = currentSession
    
    fun getCookieString(): String? = currentSession?.toCookieString()
    
    fun isLoggedIn(): Boolean = currentSession != null
    
    private fun saveSessionToStorage(session: LoggedInSession) {
        context?.let {
            val sharedPref = it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = Json.encodeToString(LoggedInSession.serializer(), session)
            sharedPref.edit().putString(SESSION_KEY, json).apply()
            Log.d("BiliTV", "Session saved to storage")
        }
    }
    
    private fun loadSessionFromStorage() {
        context?.let {
            val sharedPref = it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = sharedPref.getString(SESSION_KEY, null)
            if (json != null) {
                try {
                    currentSession = Json.decodeFromString(LoggedInSession.serializer(), json)
                    Log.d("BiliTV", "Session loaded from storage: ${currentSession?.dedeUserID}")
                } catch (e: Exception) {
                    Log.e("BiliTV", "Failed to load session from storage", e)
                    currentSession = null
                }
            }
        }
    }
    
    private fun clearSessionFromStorage() {
        context?.let {
            val sharedPref = it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPref.edit().remove(SESSION_KEY).apply()
            Log.d("BiliTV", "Session cleared from storage")
        }
    }
}

private val httpClient = OkHttpClient()
private val json = Json { ignoreUnknownKeys = true }

fun parseSessionDataFromUrl(url: String): Map<String, String> {
    val uri = Uri.parse(url)
    val params = mutableMapOf<String, String>()
    uri.queryParameterNames.forEach { name ->
        uri.getQueryParameter(name)?.let { value ->
            params[name] = value
        }
    }
    return params
}

enum class NavRoute(val title: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    CATEGORY("分类", Icons.AutoMirrored.Filled.List),
    DYNAMIC("动态", Icons.Default.Star),
    LIVE("直播", Icons.Default.PlayArrow),
    USER("用户", Icons.Default.AccountCircle),
    SETTINGS("设置", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    var currentRoute by remember { mutableStateOf(NavRoute.HOME) }
    var isFullScreenPlayer by remember { mutableStateOf(false) }
    var fullScreenPlayInfo by remember { mutableStateOf<VideoPlayInfo?>(null) }
    var fullScreenLivePlayInfo by remember { mutableStateOf<LivePlayInfo?>(null) }
    var fullScreenVideoTitle by remember { mutableStateOf("") }
    
    // 直播模块的导航状态 - 提升到顶层以保持状态
    var selectedLiveArea by remember { mutableStateOf<LiveAreaItem?>(null) }
    var liveListEnterTimestamp by remember { mutableLongStateOf(0L) }
    
    // 使用 ViewModel 保存首页状态
    val homeViewModel: HomeViewModel = viewModel()
    
    // 使用 ViewModel 保存直播分区状态，确保在导航时不会丢失
    val liveAreaViewModel: LiveAreaViewModel = viewModel()
    
    // 使用 ViewModel 保存直播列表状态，确保在导航时不会丢失
    val liveRoomListViewModel: LiveRoomListViewModel = viewModel()

    var loggedInSession by remember { mutableStateOf(SessionManager.getSession()) }
    var userInfo by remember { mutableStateOf<UserInfoData?>(null) }

    // 获取用户信息
    LaunchedEffect(loggedInSession) {
        if (loggedInSession != null && userInfo == null) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://api.bilibili.com/x/web-interface/nav")
                        .addHeader("Cookie", loggedInSession!!.toCookieString())
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            try {
                                val apiResp = json.decodeFromString<UserInfoResponse>(body)
                                if (apiResp.code == 0) {
                                    userInfo = apiResp.data
                                    // Extract and store WBI keys
                                    apiResp.data?.wbi_img?.let { wbi ->
                                        SessionManager.wbiImgKey = wbi.img_url.substringAfterLast("/").substringBefore(".")
                                        SessionManager.wbiSubKey = wbi.sub_url.substringAfterLast("/").substringBefore(".")
                                        Log.d("BiliTV", "WBI Keys extracted: ${SessionManager.wbiImgKey}, ${SessionManager.wbiSubKey}")
                                    }
                                } else {
                                    Log.e("BiliTV", "Failed to get user info: ${apiResp.message}")
                                }
                            } catch (e: Exception) {
                                Log.e("BiliTV", "Failed to parse user info", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BiliTV", "Error fetching user info", e)
                }
            }
        }
    }

    // 处理返回按钮逻辑
    BackHandler(enabled = isFullScreenPlayer) {
        // 当在全屏播放器时，返回按钮只退出播放器
        isFullScreenPlayer = false
        fullScreenPlayInfo = null
        fullScreenLivePlayInfo = null
        fullScreenVideoTitle = ""
    }

    // 非全屏播放器状态下处理返回键 - 双击返回键退出应用
    BackHandler(enabled = !isFullScreenPlayer && selectedLiveArea == null) {
        // 由于MainActivity中已经有双击返回键逻辑，这里不需要额外处理
        // MainActivity会处理应用退出逻辑
    }

    // 处理从直播房间列表返回到直播分区的逻辑
    BackHandler(enabled = !isFullScreenPlayer && selectedLiveArea != null) {
        selectedLiveArea = null
        // 返回直播分区时，标记需要恢复焦点
        liveAreaViewModel.shouldRestoreFocusToGrid = true
    }

    // 判断是否应该隐藏导航栏（直播列表页面全屏显示）
    val shouldHideNavigation = currentRoute == NavRoute.LIVE && selectedLiveArea != null

    // 使用Crossfade实现播放器与列表之间的平滑过渡
    Crossfade(
        targetState = isFullScreenPlayer,
        animationSpec = tween(durationMillis = 300),
        label = "player_transition"
    ) { showPlayer ->
        if (showPlayer) {
            if (fullScreenLivePlayInfo != null) {
                // 直播播放
                VideoPlayerScreen(
                    videoPlayInfo = fullScreenLivePlayInfo!!.toVideoPlayInfo(),
                    videoTitle = fullScreenVideoTitle,
                    onBackClick = {
                        isFullScreenPlayer = false
                        fullScreenLivePlayInfo = null
                    },
                    isLiveStream = true
                )
            } else if (fullScreenPlayInfo != null) {
                // 普通视频播放
                VideoPlayerScreen(
                    videoPlayInfo = fullScreenPlayInfo!!,
                    videoTitle = fullScreenVideoTitle,
                    onBackClick = {
                        isFullScreenPlayer = false
                        fullScreenPlayInfo = null
                    }
                )
            }
        } else {
        // 普通界面（带导航栏或全屏）
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Side Navigation - 仅在非全屏模式时显示
            if (!shouldHideNavigation) {
                NavigationRail(
                    currentRoute = currentRoute,
                    onNavigate = { currentRoute = it },
                    userAvatarUrl = userInfo?.face
                )
            }

            // Right Side Content - 使用Crossfade实现页面切换的淡入淡出动画
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Crossfade(
                    targetState = currentRoute,
                    animationSpec = tween(durationMillis = 300),
                    label = "nav_transition"
                ) { route ->
                when (route) {
                    NavRoute.HOME -> HomeScreen(
                        viewModel = homeViewModel,
                        onEnterFullScreen = { playInfo, title ->
                            isFullScreenPlayer = true
                            fullScreenPlayInfo = playInfo
                            fullScreenVideoTitle = title
                        }
                    )
                    NavRoute.USER -> UserLoginScreen(
                        loggedInSession = loggedInSession,
                        userInfo = userInfo,
                        onLoginSuccess = { session ->
                            loggedInSession = session
                            // 登录成功后，userInfo 会通过 LaunchedEffect 自动获取
                        }
                    )
                    NavRoute.DYNAMIC -> DynamicScreen(
                        loggedInSession = loggedInSession,
                        onEnterFullScreen = { playInfo, title ->
                            isFullScreenPlayer = true
                            fullScreenPlayInfo = playInfo
                            fullScreenVideoTitle = title
                        }
                    )
                    NavRoute.CATEGORY -> CategoryScreen(
                        onEnterFullScreen = { playInfo, title ->
                            isFullScreenPlayer = true
                            fullScreenPlayInfo = playInfo
                            fullScreenVideoTitle = title
                        }
                    )
                    NavRoute.LIVE -> {
                        if (selectedLiveArea == null) {
                            LiveAreaScreen(
                                viewModel = liveAreaViewModel,
                                onAreaClick = { area -> 
                                    selectedLiveArea = area 
                                    liveListEnterTimestamp = System.currentTimeMillis()
                                }
                            )
                        } else {
                            LiveRoomListScreen(
                                area = selectedLiveArea!!,
                                enterTimestamp = liveListEnterTimestamp,
                                viewModel = liveRoomListViewModel,
                                onBack = { 
                                    selectedLiveArea = null
                                    // 返回直播分区时，标记需要恢复焦点
                                    liveAreaViewModel.shouldRestoreFocusToGrid = true
                                },
                                onEnterFullScreen = { playInfo, title ->
                                    isFullScreenPlayer = true
                                    fullScreenPlayInfo = playInfo
                                    fullScreenVideoTitle = title
                                },
                                onEnterLiveRoom = { livePlayInfo, title ->
                                    isFullScreenPlayer = true
                                    fullScreenLivePlayInfo = livePlayInfo
                                    fullScreenVideoTitle = title
                                }
                            )
                        }
                    }
                    NavRoute.SETTINGS -> PlaceholderScreen(NavRoute.SETTINGS.title)
                    else -> PlaceholderScreen(route.title)
                }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun UserLoginScreen(
    loggedInSession: LoggedInSession?,
    userInfo: UserInfoData?,
    onLoginSuccess: (LoggedInSession) -> Unit
) {
    val context = LocalContext.current
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrCodeKey by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPollingActive by remember { mutableStateOf(true) } // Control polling loop

    val coroutineScope = rememberCoroutineScope()

    // LaunchedEffect to trigger API call when screen becomes active
    LaunchedEffect(Unit) { // Unit means it runs once
        if (loggedInSession != null) {
            // 如果已登录，直接显示登录状态，不再获取二维码
            isPollingActive = false
            qrCodeBitmap = null
            isLoading = false
        } else {
            // 如果未登录，获取二维码
            isLoading = true
            error = null
            coroutineScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) { // Network call on IO dispatcher
                        val request = Request.Builder()
                            .url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
                            .build()
                        httpClient.newCall(request).execute()
                    }

                    if (response.isSuccessful) {
                        response.body?.string()?.let { responseBody ->
                            val qrCodeResponse = json.decodeFromString<QrCodeResponse>(responseBody)
                            if (qrCodeResponse.code == 0) {
                                qrCodeKey = qrCodeResponse.data.qrcode_key
                                val qrUrl = qrCodeResponse.data.url
                                qrCodeBitmap = withContext(Dispatchers.Default) { // QR code generation on Default dispatcher
                                    QRCodeGenerator.generateQRCodeBitmap(qrUrl)
                                }
                            } else {
                                error = "API error: ${qrCodeResponse.message}"
                            }
                        }
                    } else {
                        error = "HTTP error: ${response.code}"
                    }
                } catch (e: Exception) {
                    error = "Network error: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Polling LaunchedEffect
    LaunchedEffect(qrCodeKey, isPollingActive) { // Depend on qrCodeKey and isPollingActive
        if (qrCodeKey != null && isPollingActive) {
            while (isActive && isPollingActive) {
                delay(5000L) // Poll every 5 seconds
                coroutineScope.launch {
                    try {
                        val pollUrl = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrCodeKey"
                        val request = Request.Builder()
                            .url(pollUrl)
                            .build()
                        val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }

                        if (response.isSuccessful) {
                            response.body?.string()?.let { responseBody ->
                                Log.d("BiliTV", "Poll Response: $responseBody")
                                val pollResponse = json.decodeFromString<QrCodePollResponse>(responseBody)

                                // Check for login success code (0 in data.code)
                                if (pollResponse.data?.code == 0) {
                                    val sessionData = parseSessionDataFromUrl(pollResponse.data.url ?: "")
                                    val dedeUserID = sessionData["DedeUserID"] ?: ""
                                    val dedeUserIDCkMd5 = sessionData["DedeUserID__ckMd5"] ?: ""
                                    val sessdata = sessionData["SESSDATA"] ?: ""
                                    val biliJct = sessionData["bili_jct"] ?: ""
                                    val expires = sessionData["Expires"]?.toLongOrNull() ?: 0L
                                    val refreshToken = pollResponse.data.refresh_token ?: ""
                                    val crossDomainUrl = pollResponse.data.url ?: ""

                                    val session = LoggedInSession(
                                        dedeUserID = dedeUserID,
                                        dedeUserIDCkMd5 = dedeUserIDCkMd5,
                                        sessdata = sessdata,
                                        biliJct = biliJct,
                                        expires = expires,
                                        refreshToken = refreshToken,
                                        crossDomainUrl = crossDomainUrl
                                    )
                                    // 保存到全局会话管理器
                                    SessionManager.setSession(session)
                                    onLoginSuccess(session) // 通知 MainScreen
                                    isPollingActive = false // Stop polling on success
                                    qrCodeBitmap = null // Hide QR code
                                    error = null // Clear any errors
                                    Log.d("BiliTV", "Login successful! Session: $session")
                                } else if (pollResponse.data?.code == 86038) { // Expired
                                    error = "QR Code expired. Please refresh."
                                    isPollingActive = false
                                    qrCodeBitmap = null
                                }
                                // Other codes might mean "scanned, waiting for confirmation" - continue polling
                            }
                        } else {
                            Log.e("BiliTV", "Poll HTTP error: ${response.code}")
                        }
                    } catch (e: Exception) {
                        Log.e("BiliTV", "Poll Network error: ${e.localizedMessage}")
                        error = "Polling error: ${e.localizedMessage}" // Display polling errors
                    }
                }
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            loggedInSession != null -> { // Login successful
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (userInfo != null) {
                        AsyncImage(
                            model = userInfo!!.face,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(userInfo!!.uname, style = MaterialTheme.typography.displaySmall)
                    } else {
                        CircularProgressIndicator()
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("登录成功!", style = MaterialTheme.typography.titleMedium)
                    Text("UID: ${loggedInSession?.dedeUserID}", style = MaterialTheme.typography.bodySmall)
                }
            }
            error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error)
            qrCodeBitmap != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrCodeBitmap!!.asImageBitmap(),
                        contentDescription = "QR Code for Login",
                        modifier = Modifier.size(256.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("请使用B站App扫码登录", style = MaterialTheme.typography.bodyLarge)
                    qrCodeKey?.let {
                        Text("QR Code Key: $it", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            else -> Text("无法加载登录二维码")
        }
    }
}

@Composable
fun NavigationRail(
    currentRoute: NavRoute,
    onNavigate: (NavRoute) -> Unit,
    modifier: Modifier = Modifier,
    userAvatarUrl: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(50.dp) // Fixed width for the side menu (more narrow)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp), // Reduced padding to match narrower menu
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top // Allow content to be pushed to bottom
    ) {
        // Top navigation items
        Column(
            modifier = Modifier.weight(1f), // This pushes the following content to bottom
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            NavButton(
                icon = NavRoute.HOME.icon,
                label = NavRoute.HOME.title,
                selected = currentRoute == NavRoute.HOME,
                onClick = { onNavigate(NavRoute.HOME) }
            )
            NavButton(
                icon = NavRoute.CATEGORY.icon,
                label = NavRoute.CATEGORY.title,
                selected = currentRoute == NavRoute.CATEGORY,
                onClick = { onNavigate(NavRoute.CATEGORY) }
            )
            NavButton(
                icon = NavRoute.DYNAMIC.icon,
                label = NavRoute.DYNAMIC.title,
                selected = currentRoute == NavRoute.DYNAMIC,
                onClick = { onNavigate(NavRoute.DYNAMIC) }
            )
            NavButton(
                icon = NavRoute.LIVE.icon,
                label = NavRoute.LIVE.title,
                selected = currentRoute == NavRoute.LIVE,
                onClick = { onNavigate(NavRoute.LIVE) }
            )
        }

        // Bottom navigation items
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp) // Spacing between bottom items
        ) {
            NavButton(
                icon = NavRoute.USER.icon,
                label = NavRoute.USER.title,
                selected = currentRoute == NavRoute.USER,
                onClick = { onNavigate(NavRoute.USER) },
                avatarUrl = userAvatarUrl
            )
            NavButton(
                icon = NavRoute.SETTINGS.icon,
                label = NavRoute.SETTINGS.title,
                selected = currentRoute == NavRoute.SETTINGS,
                onClick = { onNavigate(NavRoute.SETTINGS) }
            )
        }
    }
}

@Composable
fun NavButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    avatarUrl: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(32.dp), // Further reduced button size for narrower menu
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(0.dp),
            border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    modifier = Modifier
                        .size(16.dp) // Reduced avatar size for narrower menu
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp) // Further reduced icon size for narrower menu
                )
            }
        }

        if (isFocused) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

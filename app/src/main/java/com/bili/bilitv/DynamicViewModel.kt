package com.bili.bilitv

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bili.bilitv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bili.bilitv.utils.WbiUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

@Serializable
data class FollowingListResponse(
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: FollowingListData? = null
)

@Serializable
data class FollowingListData(
    val list: List<FollowingUser>? = null,
    val total: Int = 0
)

@Serializable
data class FollowingUser(
    val mid: Long,
    val uname: String,
    val face: String,
    val sign: String = "",
    val official_verify: OfficialVerify? = null
)

@Serializable
data class OfficialVerify(
    val type: Int = -1,
    val desc: String = ""
)

@Serializable
data class NavResponse(val code: Int, val data: NavData? = null)

@Serializable
data class NavData(val wbi_img: WbiImg? = null)

@Serializable
data class SpaceSearchResponse(val code: Int, val message: String, val data: SpaceSearchData? = null)

@Serializable
data class SpaceSearchData(val list: SpaceSearchList? = null)

@Serializable
data class SpaceSearchList(val vlist: List<SpaceVideoItem>? = null)

@Serializable
data class SpaceVideoItem(
    val aid: Long,
    val bvid: String,
    val title: String,
    val pic: String,
    val author: String,
    val created: Long,
    val length: String,
    val description: String = ""
) {
    fun toVideo(): Video {
        return Video(
            id = aid.toString(),
            bvid = bvid,
            title = title,
            coverUrl = if (pic.startsWith("//")) "https:$pic" else pic,
            author = author,
            playCount = "", // SpaceSearch API doesn't return play count directly in vlist item easily without extra call or fields check, leaving empty or need to update data class
            duration = length,
            pubDate = created
        )
    }
}

@Serializable
data class DynamicAllResponse(val code: Int, val data: DynamicAllData? = null, val message: String = "")

@Serializable
data class DynamicAllData(
    val items: List<DynamicItem>? = null,
    val offset: String = ""
)

@Serializable
data class DynamicItem(
    val id_str: String,
    val type: String,
    val modules: DynamicModules
)

@Serializable
data class DynamicModules(
    val module_author: ModuleAuthor,
    val module_dynamic: ModuleDynamic
)

@Serializable
data class ModuleAuthor(
    val name: String,
    val pub_ts: Long
)

@Serializable
data class ModuleDynamic(
    val major: ModuleMajor? = null
)

@Serializable
data class ModuleMajor(
    val archive: DynamicArchive? = null,
    val type: String // "MAJOR_TYPE_ARCHIVE"
)

@Serializable
data class DynamicArchive(
    val aid: String,
    val bvid: String,
    val title: String,
    val cover: String,
    val stat: DynamicStat,
    val duration_text: String = ""
)

@Serializable
data class DynamicStat(
    val play: String,
    val danmaku: String
)

class DynamicViewModel : ViewModel(), VideoGridStateManager {
    var followingList by mutableStateOf<List<FollowingUser>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selectedUser by mutableStateOf<FollowingUser?>(null)
    var isAllDynamicsSelected by mutableStateOf(false)
    
    var userVideos by mutableStateOf<List<Video>>(emptyList())
    var isVideoLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var refreshSignal by mutableStateOf(0)
    
    // Scroll and Focus State Persistence
    var videoListScrollIndex by mutableStateOf(0)
    var videoListScrollOffset by mutableStateOf(0)
    var lastFocusedVideoId by mutableStateOf<String?>(null)
    
    // Pagination state
    private var currentPage = 1
    private var dynamicOffset: String = ""
    var hasMoreVideos by mutableStateOf(true)

    private var currentCookie: String = ""
    private var cachedImgKey: String? = null
    private var cachedSubKey: String? = null

    // VideoGridStateManager 接口实现
    override fun updateScrollState(key: Any, index: Int, offset: Int) {
        videoListScrollIndex = index
        videoListScrollOffset = offset
    }

    override fun getScrollState(key: Any): Pair<Int, Int> {
        return videoListScrollIndex to videoListScrollOffset
    }

    override fun updateFocusedIndex(key: Any, index: Int) {
        // For dynamic screen, we track focus by video ID
        if (index >= 0 && index < userVideos.size) {
            lastFocusedVideoId = userVideos[index].id
        }
    }

    override fun getFocusedIndex(key: Any): Int {
        // Find the index of the last focused video
        val focusedId = lastFocusedVideoId ?: return -1
        return userVideos.indexOfFirst { it.id == focusedId }
    }

    override var shouldRestoreFocusToGrid: Boolean = false

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchFollowings(mid: String, cookie: String) {
        if (isLoading) return
        isLoading = true
        error = null
        currentCookie = cookie

        viewModelScope.launch {
            try {
                // Ensure we have Wbi keys
                ensureWbiKeys()

                if (cachedImgKey != null && cachedSubKey != null) {
                    try {
                        val params = mapOf(
                            "vmid" to mid,
                            "pn" to "1",
                            "ps" to "50",
                            "order" to "desc"
                        )
                        val signedParams = WbiUtil.sign(params, cachedImgKey!!, cachedSubKey!!)
                        
                        val urlBuilder = StringBuilder("https://api.bilibili.com/x/relation/followings?")
                        signedParams.forEach { (k, v) ->
                            urlBuilder.append(k).append("=").append(WbiUtil.encodeURIComponent(v)).append("&")
                        }
                        val url = urlBuilder.toString().trimEnd('&')

                        val response = withContext(Dispatchers.IO) {
                            if (BuildConfig.DEBUG) {
                                Log.d("BiliTV", "Requesting followings list (WBI): $url")
                            }
                            val request = Request.Builder()
                                .url(url)
                                .addHeader("Cookie", cookie)
                                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .addHeader("Referer", "https://space.bilibili.com/$mid/fans/follow")
                                .build()
                            httpClient.newCall(request).execute()
                        }
                        
                        if (handleFollowingsResponse(response)) {
                            return@launch
                        }
                    } catch(e: Exception) {
                        Log.e("BiliTV", "WBI fetch failed", e)
                    }
                }

                // Fallback to original
                 val response = withContext(Dispatchers.IO) {
                    val url = "https://api.bilibili.com/x/relation/followings?vmid=$mid&pn=1&ps=50&order=desc"
                    if (BuildConfig.DEBUG) {
                        Log.d("BiliTV", "Requesting followings list (Fallback): $url")
                    }
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Cookie", cookie)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .addHeader("Referer", "https://space.bilibili.com/$mid/fans/follow")
                        .build()
                    httpClient.newCall(request).execute()
                }
                handleFollowingsResponse(response)

            } catch (e: Exception) {
                error = "Network Error: ${e.localizedMessage}"
                Log.e("BiliTV", "Error fetching followings", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun handleFollowingsResponse(response: okhttp3.Response): Boolean {
        if (response.isSuccessful) {
            val body = response.body?.string()
            if (BuildConfig.DEBUG) {
                Log.d("BiliTV", "Response body: $body")
            }
            if (body != null) {
                try {
                    val apiResp = json.decodeFromString<FollowingListResponse>(body)
                    if (apiResp.code == 0) {
                        followingList = apiResp.data?.list ?: emptyList()
                        if (BuildConfig.DEBUG) {
                            Log.d("BiliTV", "Parsed ${followingList.size} followings")
                        }
                        // If no user selected and not showing all dynamics, select all dynamics
                        if (selectedUser == null && !isAllDynamicsSelected) {
                            selectAllDynamics()
                        }
                        return true
                    } else {
                        error = "API Error: ${apiResp.message}"
                        Log.e("BiliTV", "Following API error: ${apiResp.message}")
                    }
                } catch (e: Exception) {
                    Log.e("BiliTV", "JSON Parse Error", e)
                    error = "Parse Error: ${e.localizedMessage}"
                }
            }
        } else {
            error = "HTTP Error: ${response.code}"
        }
        return false
    }
    
    fun selectUser(user: FollowingUser) {
        selectedUser = user
        isAllDynamicsSelected = false
        // Reset pagination and scroll state
        currentPage = 1
        hasMoreVideos = true
        userVideos = emptyList()
        videoListScrollIndex = 0
        videoListScrollOffset = 0
        lastFocusedVideoId = null
        shouldRestoreFocusToGrid = false // Don't auto-focus when switching user
        fetchUserVideos(user.mid, 1, isRefresh = true)
    }

    fun selectAllDynamics() {
        isAllDynamicsSelected = true
        selectedUser = null
        currentPage = 1
        hasMoreVideos = true
        userVideos = emptyList()
        dynamicOffset = ""
        videoListScrollIndex = 0
        videoListScrollOffset = 0
        lastFocusedVideoId = null
        shouldRestoreFocusToGrid = false // Don't auto-focus when switching to all dynamics
        fetchAllDynamics(isRefresh = true)
    }

    fun refreshCurrent() {
        if (isRefreshing || isVideoLoading) return
        if (!isAllDynamicsSelected && selectedUser == null) return
        isRefreshing = true
        shouldRestoreFocusToGrid = true
        refreshSignal++
        currentPage = 1
        dynamicOffset = ""
        hasMoreVideos = true
        videoListScrollIndex = 0
        videoListScrollOffset = 0
        lastFocusedVideoId = null
        if (isAllDynamicsSelected) {
            fetchAllDynamics(isRefresh = true) { isRefreshing = false }
        } else {
            selectedUser?.let { fetchUserVideos(it.mid, 1, isRefresh = true) { isRefreshing = false } }
                ?: run { isRefreshing = false }
        }
    }

    fun loadMoreVideos() {
        if (isVideoLoading || !hasMoreVideos) return
        if (isAllDynamicsSelected) {
            fetchAllDynamics()
        } else if (selectedUser != null) {
            currentPage++
            fetchUserVideos(selectedUser!!.mid, currentPage)
        }
    }

    private fun fetchAllDynamics(isRefresh: Boolean = false, onComplete: (() -> Unit)? = null) {
        if (isVideoLoading) return
        isVideoLoading = true

        viewModelScope.launch {
            try {
                val urlBuilder = StringBuilder("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?timezone_offset=-480&type=all")
                if (!isRefresh && dynamicOffset.isNotEmpty()) {
                    urlBuilder.append("&offset=").append(dynamicOffset)
                }
                val url = urlBuilder.toString()

                if (BuildConfig.DEBUG) {
                    Log.d("BiliTV", "Requesting all dynamics: $url")
                }

                val response = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Cookie", currentCookie)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val apiResp = json.decodeFromString<DynamicAllResponse>(body)
                        if (apiResp.code == 0) {
                            val items = apiResp.data?.items ?: emptyList()
                            dynamicOffset = apiResp.data?.offset ?: ""
                            
                            val newVideos = items.mapNotNull { item ->
                                if (item.type == "DYNAMIC_TYPE_AV" && item.modules.module_dynamic.major?.type == "MAJOR_TYPE_ARCHIVE") {
                                    val archive = item.modules.module_dynamic.major.archive
                                    if (archive != null) {
                                        Video(
                                            id = archive.aid,
                                            bvid = archive.bvid,
                                            title = archive.title,
                                            coverUrl = archive.cover,
                                            author = item.modules.module_author.name,
                                            pubDate = item.modules.module_author.pub_ts,
                                            playCount = archive.stat.play,
                                            danmakuCount = archive.stat.danmaku,
                                            duration = archive.duration_text
                                        )
                                    } else null
                                } else null
                            }

                            if (apiResp.data?.items.isNullOrEmpty()) {
                                hasMoreVideos = false
                            } else {
                                userVideos = if (isRefresh) newVideos else userVideos + newVideos
                            }
                            if (BuildConfig.DEBUG) {
                                Log.d("BiliTV", "Parsed ${newVideos.size} videos from dynamics")
                            }
                        } else {
                            Log.e("BiliTV", "All Dynamics API Error: ${apiResp.message}")
                        }
                    }
                } else {
                    Log.e("BiliTV", "All Dynamics HTTP Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("BiliTV", "Error fetching all dynamics", e)
            } finally {
                isVideoLoading = false
                onComplete?.invoke()
            }
        }
    }

    private fun fetchUserVideos(mid: Long, page: Int, isRefresh: Boolean = false, onComplete: (() -> Unit)? = null) {
        if (isVideoLoading) return
        isVideoLoading = true
        
        viewModelScope.launch {
            try {
                // Ensure we have Wbi keys
                ensureWbiKeys()
                
                if (cachedImgKey == null || cachedSubKey == null) {
                    Log.e("BiliTV", "Failed to get WBI keys")
                    return@launch
                }

                val params = mapOf(
                    "mid" to mid.toString(),
                    "ps" to "30",
                    "tid" to "0",
                    "pn" to page.toString(),
                    "keyword" to "",
                    "order" to "pubdate"
                )
                
                val signedParams = WbiUtil.sign(params, cachedImgKey!!, cachedSubKey!!)
                
                val urlBuilder = StringBuilder("https://api.bilibili.com/x/space/wbi/arc/search?")
                signedParams.forEach { (k, v) ->
                    urlBuilder.append(k).append("=").append(WbiUtil.encodeURIComponent(v)).append("&")
                }
                val url = urlBuilder.toString().trimEnd('&')
                
                if (BuildConfig.DEBUG) {
                    Log.d("BiliTV", "Requesting user videos: $url")
                }
                
                val response = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Cookie", currentCookie)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .addHeader("Referer", "https://space.bilibili.com/$mid/video")
                        .build()
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    // Log.d("BiliTV", "Video response body: $body")
                    if (body != null) {
                        val apiResp = json.decodeFromString<SpaceSearchResponse>(body)
                        if (apiResp.code == 0) {
                            val vlist = apiResp.data?.list?.vlist ?: emptyList()
                            val newVideos = vlist.map { it.toVideo() }
                            
                            if (newVideos.isEmpty()) {
                                hasMoreVideos = false
                            } else {
                                if (page == 1 || isRefresh) {
                                    userVideos = newVideos
                                } else {
                                    userVideos = userVideos + newVideos
                                }
                            }
                            if (BuildConfig.DEBUG) {
                                Log.d("BiliTV", "Parsed ${newVideos.size} videos for user $mid page $page")
                            }
                        } else {
                            Log.e("BiliTV", "Space API Error: ${apiResp.message}")
                        }
                    }
                } else {
                    Log.e("BiliTV", "Space HTTP Error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("BiliTV", "Error fetching user videos", e)
            } finally {
                isVideoLoading = false
                onComplete?.invoke()
            }
        }
    }

    private suspend fun ensureWbiKeys() {
        if (cachedImgKey != null && cachedSubKey != null) return
        
        try {
            val response = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://api.bilibili.com/x/web-interface/nav")
                    .addHeader("Cookie", currentCookie)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                httpClient.newCall(request).execute()
            }
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val navResp = json.decodeFromString<NavResponse>(body)
                    if (navResp.code == 0 && navResp.data?.wbi_img != null) {
                        val imgUrl = navResp.data.wbi_img.img_url
                        val subUrl = navResp.data.wbi_img.sub_url
                        
                        cachedImgKey = getKeyFromUrl(imgUrl)
                        cachedSubKey = getKeyFromUrl(subUrl)
                        if (BuildConfig.DEBUG) {
                            Log.d("BiliTV", "Got WBI keys: $cachedImgKey, $cachedSubKey")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BiliTV", "Error fetching Nav info for WBI", e)
        }
    }
    
    private fun getKeyFromUrl(url: String): String {
        return try {
             url.substringAfterLast('/').substringBefore('.')
        } catch (e: Exception) {
            ""
        }
    }
    
    fun onEnterFullScreen() {
        // Prepare to restore focus when coming back from player
        shouldRestoreFocusToGrid = true
    }
}


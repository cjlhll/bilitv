package com.bili.bilitv

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
data class WbiImg(val img_url: String, val sub_url: String)

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
            pubDate = created
        )
    }
}

class DynamicViewModel : ViewModel() {
    var followingList by mutableStateOf<List<FollowingUser>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selectedUser by mutableStateOf<FollowingUser?>(null)
    
    var userVideos by mutableStateOf<List<Video>>(emptyList())
    var isVideoLoading by mutableStateOf(false)
    
    // Pagination state
    private var currentPage = 1
    var hasMoreVideos by mutableStateOf(true)

    private var currentCookie: String = ""
    private var cachedImgKey: String? = null
    private var cachedSubKey: String? = null

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
                            Log.d("BiliTV", "Requesting followings list (WBI): $url")
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
                    Log.d("BiliTV", "Requesting followings list (Fallback): $url")
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
            Log.d("BiliTV", "Response body: $body")
            if (body != null) {
                try {
                    val apiResp = json.decodeFromString<FollowingListResponse>(body)
                    if (apiResp.code == 0) {
                        followingList = apiResp.data?.list ?: emptyList()
                        Log.d("BiliTV", "Parsed ${followingList.size} followings")
                        // Select first user by default if list is not empty and no user selected
                        if (selectedUser == null && followingList.isNotEmpty()) {
                            selectUser(followingList[0])
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
        // Reset pagination
        currentPage = 1
        hasMoreVideos = true
        userVideos = emptyList()
        fetchUserVideos(user.mid, 1)
    }

    fun loadMoreVideos() {
        if (isVideoLoading || !hasMoreVideos || selectedUser == null) return
        currentPage++
        fetchUserVideos(selectedUser!!.mid, currentPage)
    }

    private fun fetchUserVideos(mid: Long, page: Int) {
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
                
                Log.d("BiliTV", "Requesting user videos: $url")
                
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
                                if (page == 1) {
                                    userVideos = newVideos
                                } else {
                                    userVideos = userVideos + newVideos
                                }
                            }
                            Log.d("BiliTV", "Parsed ${newVideos.size} videos for user $mid page $page")
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
                        Log.d("BiliTV", "Got WBI keys: $cachedImgKey, $cachedSubKey")
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
}


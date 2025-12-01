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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

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

class DynamicViewModel : ViewModel() {
    var followingList by mutableStateOf<List<FollowingUser>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selectedUser by mutableStateOf<FollowingUser?>(null)

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchFollowings(mid: String, cookie: String) {
        if (isLoading) return
        isLoading = true
        error = null

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val url = "https://api.bilibili.com/x/relation/followings?vmid=$mid&pn=1&ps=50&order=desc"
                    Log.d("BiliTV", "Requesting followings list: $url")
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Cookie", cookie)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .addHeader("Referer", "https://space.bilibili.com/$mid/fans/follow")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    Log.d("BiliTV", "Response code: ${response.code}")
                    response
                }

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
                                    selectedUser = followingList[0]
                                }
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
            } catch (e: Exception) {
                error = "Network Error: ${e.localizedMessage}"
                Log.e("BiliTV", "Error fetching followings", e)
            } finally {
                isLoading = false
            }
        }
    }
    
    fun selectUser(user: FollowingUser) {
        selectedUser = user
    }
}


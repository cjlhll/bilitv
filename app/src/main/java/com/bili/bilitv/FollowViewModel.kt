package com.bili.bilitv

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class FollowViewModel : ViewModel(), VideoGridStateManager {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://api.bilibili.com/x/space/bangumi/follow/list"

    // 追番 (Anime) state
    var animeList = mutableStateListOf<FollowItem>()
        private set
    var isAnimeLoading by mutableStateOf(false)
        private set
    var animeError by mutableStateOf<String?>(null)
        private set
    var animePage by mutableStateOf(1)
    var animeTotal by mutableStateOf(0)
    var hasMoreAnime by mutableStateOf(true)

    // 追剧 (Cinema) state
    var cinemaList = mutableStateListOf<FollowItem>()
        private set
    var isCinemaLoading by mutableStateOf(false)
        private set
    var cinemaError by mutableStateOf<String?>(null)
        private set
    var cinemaPage by mutableStateOf(1)
    var cinemaTotal by mutableStateOf(0)
    var hasMoreCinema by mutableStateOf(true)

    // VideoGridStateManager implementation
    override var shouldRestoreFocusToGrid by mutableStateOf(false)

    // focused index for grid state restoration
    private val focusedIndices = mutableMapOf<Any, Int>()
    // scroll state
    private val scrollStates = mutableMapOf<Any, Pair<Int, Int>>()

    override fun updateScrollState(key: Any, index: Int, offset: Int) {
        scrollStates[key] = index to offset
    }

    override fun getScrollState(key: Any): Pair<Int, Int> {
        return scrollStates[key] ?: (0 to 0)
    }

    override fun getFocusedIndex(key: Any): Int {
        return focusedIndices[key] ?: 0
    }

    override fun updateFocusedIndex(key: Any, index: Int) {
        focusedIndices[key] = index
    }

    fun loadAnimeList(refresh: Boolean = false) {
        if (refresh) {
            animePage = 1
            animeList.clear()
            hasMoreAnime = true
        }
        if (!hasMoreAnime || isAnimeLoading) return

        viewModelScope.launch {
            isAnimeLoading = true
            animeError = null
            try {
                fetchFollowList(0, animePage)
            } catch (e: Exception) {
                animeError = e.message
                Log.e("FollowViewModel", "Error fetching anime list", e)
            } finally {
                isAnimeLoading = false
            }
        }
    }

    fun loadCinemaList(refresh: Boolean = false) {
        if (refresh) {
            cinemaPage = 1
            cinemaList.clear()
            hasMoreCinema = true
        }
        if (!hasMoreCinema || isCinemaLoading) return

        viewModelScope.launch {
            isCinemaLoading = true
            cinemaError = null
            try {
                fetchFollowList(1, cinemaPage)
            } catch (e: Exception) {
                cinemaError = e.message
                Log.e("FollowViewModel", "Error fetching cinema list", e)
            } finally {
                isCinemaLoading = false
            }
        }
    }

    private suspend fun fetchFollowList(type: Int, page: Int) {
        val session = SessionManager.getSession()
        if (session == null) {
            throw Exception("Not logged in")
        }

        // Bilibili API type: 1 - Bangumi, 2 - Cinema
        val apiType = if (type == 0) 1 else 2
        
        val params = mutableMapOf(
            "vmid" to session.dedeUserID,
            "type" to apiType.toString(),
            "pn" to page.toString(),
            "ps" to "20",
            "follow_status" to "0",
            "playform" to "web",
            "web_location" to "333.1387"
        )

        // Sign with WBI if keys are available
        val finalParams = if (SessionManager.wbiImgKey != null && SessionManager.wbiSubKey != null) {
            com.bili.bilitv.utils.WbiUtil.sign(
                params,
                SessionManager.wbiImgKey!!,
                SessionManager.wbiSubKey!!
            )
        } else {
            params
        }

        val queryString = finalParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val url = "$baseUrl?$queryString"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", session.toCookieString())
            .addHeader("Referer", "https://space.bilibili.com/${session.dedeUserID}/bangumi")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        withContext(Dispatchers.IO) {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                throw Exception("HTTP Error: ${response.code}")
            }

            val apiResponse = json.decodeFromString<FollowListResponse>(body)
            if (apiResponse.code == 0 && apiResponse.data != null) {
                val newData = apiResponse.data.list
                val total = apiResponse.data.total

                withContext(Dispatchers.Main) {
                    if (type == 0) {
                        if (page == 1) animeList.clear()
                        animeList.addAll(newData)
                        animeTotal = total
                        hasMoreAnime = animeList.size < total
                        if (newData.isNotEmpty()) animePage++
                    } else {
                        if (page == 1) cinemaList.clear()
                        cinemaList.addAll(newData)
                        cinemaTotal = total
                        hasMoreCinema = cinemaList.size < total
                        if (newData.isNotEmpty()) cinemaPage++
                    }
                }
            } else {
                throw Exception("API Error: ${apiResponse.message}")
            }
        }
    }
}

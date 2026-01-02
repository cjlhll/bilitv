package com.bili.bilitv

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bili.bilitv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class FavoriteViewModel(application: Application) : AndroidViewModel(application), VideoGridStateManager {
    private val _favoriteFolders = mutableStateListOf<FavoriteFolder>()
    val favoriteFolders: SnapshotStateList<FavoriteFolder> = _favoriteFolders

    private val _favoriteMedias = mutableStateListOf<FavoriteMedia>()
    val favoriteMedias: SnapshotStateList<FavoriteMedia> = _favoriteMedias

    var currentFolderInfo by mutableStateOf<FavoriteFolderInfo?>(null)

    var isLoadingFolders by mutableStateOf(false)
    var isLoadingMedias by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMoreMedias by mutableStateOf(true)
    var refreshSignal by mutableStateOf(0)
    override var shouldRestoreFocusToGrid by mutableStateOf(false)

    private var currentMediaId: Long = 0
    private var currentPn: Int = 0

    private val _scrollStates = mutableStateMapOf<Any, Pair<Int, Int>>()
    private val _focusStates = mutableStateMapOf<Any, Int>()

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun loadFavoriteFolders() {
        if (isLoadingFolders) return
        viewModelScope.launch {
            isLoadingFolders = true
            error = null
            try {
                val cookie = SessionManager.getCookieString()
                if (cookie.isNullOrEmpty()) {
                    error = "未登录"
                    isLoadingFolders = false
                    return@launch
                }

                val userInfo = SessionManager.getAppContext()?.let { 
                    val sharedPref = it.getSharedPreferences("bili_session", android.content.Context.MODE_PRIVATE)
                    val sessionJson = sharedPref.getString("logged_in_session", null)
                    if (sessionJson != null) {
                        try {
                            Json.decodeFromString(LoggedInSession.serializer(), sessionJson)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }

                val mid = userInfo?.dedeUserID?.toLongOrNull() ?: 0L
                if (mid == 0L) {
                    error = "无法获取用户ID"
                    isLoadingFolders = false
                    return@launch
                }

                val url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookie)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        val folderResponse = json.decodeFromString<FavoriteFolderResponse>(responseBody)
                        if (folderResponse.code == 0) {
                            folderResponse.data?.let { data ->
                                if (BuildConfig.DEBUG) {
                                    Log.d("FavoriteViewModel", "Loaded ${data.list?.size ?: 0} favorite folders")
                                }
                                _favoriteFolders.clear()
                                data.list?.let { _favoriteFolders.addAll(it) }
                            }
                        } else {
                            error = folderResponse.message
                        }
                    }
                } else {
                    error = "HTTP error: ${response.code}"
                }
            } catch (e: Exception) {
                Log.e("FavoriteViewModel", "Failed to load favorite folders", e)
                error = "加载失败: ${e.localizedMessage}"
            } finally {
                isLoadingFolders = false
            }
        }
    }

    fun loadFavoriteFolderContents(mediaId: Long) {
        if (isLoadingMedias) return
        viewModelScope.launch {
            isLoadingMedias = true
            error = null
            try {
                val cookie = SessionManager.getCookieString()
                if (cookie.isNullOrEmpty()) {
                    error = "未登录"
                    isLoadingMedias = false
                    return@launch
                }

                if (currentMediaId == 0L || currentMediaId != mediaId) {
                    currentMediaId = mediaId
                    currentPn = 0
                    _favoriteMedias.clear()
                    hasMoreMedias = true
                }

                if (!hasMoreMedias) {
                    isLoadingMedias = false
                    return@launch
                }

                currentPn++
                val url = "https://api.bilibili.com/x/v3/fav/resource/list?media_id=$mediaId&pn=$currentPn&ps=20&keyword=&mtype=0"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookie)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        if (BuildConfig.DEBUG) {
                            Log.d("FavoriteViewModel", "Favorite medias API response: $responseBody")
                        }
                        val mediaResponse = json.decodeFromString<FavoriteMediaResponse>(responseBody)
                        if (mediaResponse.code == 0) {
                            mediaResponse.data?.let { data ->
                                if (BuildConfig.DEBUG) {
                                    Log.d("FavoriteViewModel", "Loaded ${data.medias?.size ?: 0} favorite medias, has_more=${data.has_more}")
                                }
                                currentFolderInfo = data.info
                                data.medias?.let { _favoriteMedias.addAll(it) }
                                hasMoreMedias = data.has_more
                            }
                        } else {
                            error = mediaResponse.message
                        }
                    }
                } else {
                    error = "HTTP error: ${response.code}"
                }
            } catch (e: Exception) {
                Log.e("FavoriteViewModel", "Failed to load favorite medias", e)
                error = "加载失败: ${e.localizedMessage}"
            } finally {
                isLoadingMedias = false
            }
        }
    }

    fun refreshFolders() {
        _favoriteFolders.clear()
        error = null
        updateScrollState(UserTabType.FAVORITE, 0, 0)
        updateFocusedIndex(UserTabType.FAVORITE, 0)
        refreshSignal++
        loadFavoriteFolders()
    }

    fun refreshMedias(mediaId: Long) {
        currentMediaId = 0
        currentPn = 0
        _favoriteMedias.clear()
        hasMoreMedias = true
        error = null
        loadFavoriteFolderContents(mediaId)
    }

    override fun updateScrollState(key: Any, index: Int, offset: Int) {
        _scrollStates[key] = index to offset
    }

    override fun updateFocusedIndex(key: Any, index: Int) {
        _focusStates[key] = index
    }

    override fun getScrollState(key: Any): Pair<Int, Int> {
        return _scrollStates[key] ?: (0 to 0)
    }

    override fun getFocusedIndex(key: Any): Int {
        return _focusStates[key] ?: -1
    }
}
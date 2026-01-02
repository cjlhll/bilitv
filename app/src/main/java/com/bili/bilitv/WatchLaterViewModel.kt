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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class WatchLaterViewModel(application: Application) : AndroidViewModel(application), VideoGridStateManager {
    private val _toviewItems = mutableStateListOf<ToviewItem>()
    val toviewItems: SnapshotStateList<ToviewItem> = _toviewItems

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var refreshSignal by mutableStateOf(0)
    override var shouldRestoreFocusToGrid by mutableStateOf(false)

    private val _scrollStates = mutableStateMapOf<Any, Pair<Int, Int>>()
    private val _focusStates = mutableStateMapOf<Any, Int>()

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadToview()
    }

    fun loadToview() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val cookie = SessionManager.getCookieString()
                if (cookie.isNullOrEmpty()) {
                    error = "未登录"
                    isLoading = false
                    return@launch
                }

                val url = "https://api.bilibili.com/x/v2/history/toview"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookie)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        if (BuildConfig.DEBUG) {
                            Log.d("WatchLaterViewModel", "原始API响应: $responseBody")
                        }
                        val toviewResponse = json.decodeFromString<ToviewResponse>(responseBody)
                        if (toviewResponse.code == 0) {
                            toviewResponse.data?.let { data ->
                                if (BuildConfig.DEBUG) {
                                    Log.d("WatchLaterViewModel", "Loaded ${data.list.size} toview items")
                                    data.list.forEach { item ->
                                        Log.d("WatchLaterViewModel", "稍后观看: 标题=${item.title}, aid=${item.aid}, bvid=${item.bvid}, author=${item.author}, owner=${item.owner?.name}, badge=${item.badge}")
                                    }
                                }
                                _toviewItems.clear()
                                _toviewItems.addAll(data.list)
                            }
                        } else {
                            error = toviewResponse.message
                        }
                    }
                } else {
                    error = "HTTP error: ${response.code}"
                }
            } catch (e: Exception) {
                Log.e("WatchLaterViewModel", "Failed to load toview", e)
                error = "加载失败: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteToviewItem(aid: Long) {
        viewModelScope.launch {
            try {
                val cookie = SessionManager.getCookieString()
                if (cookie.isNullOrEmpty()) {
                    error = "未登录"
                    return@launch
                }

                val url = "https://api.bilibili.com/x/v2/history/toview/del"

                val formBody = FormBody.Builder()
                    .add("aid", aid.toString())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookie)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .post(formBody)
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        val toviewResponse = json.decodeFromString<ToviewResponse>(responseBody)
                        if (toviewResponse.code == 0) {
                            _toviewItems.removeIf { it.aid == aid }
                            if (BuildConfig.DEBUG) {
                                Log.d("WatchLaterViewModel", "Deleted toview item: $aid")
                            }
                        } else {
                            error = toviewResponse.message
                        }
                    }
                } else {
                    error = "HTTP error: ${response.code}"
                }
            } catch (e: Exception) {
                Log.e("WatchLaterViewModel", "Failed to delete toview item", e)
                error = "删除失败: ${e.localizedMessage}"
            }
        }
    }

    fun refresh() {
        _toviewItems.clear()
        error = null
        updateScrollState(UserTabType.WATCH_LATER, 0, 0)
        updateFocusedIndex(UserTabType.WATCH_LATER, 0)
        refreshSignal++
        loadToview()
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

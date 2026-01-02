package com.bili.bilitv

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bili.bilitv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class HistoryViewModel(application: Application) : AndroidViewModel(application), VideoGridStateManager {
    private val _historyItems = mutableStateListOf<HistoryItem>()
    val historyItems: SnapshotStateList<HistoryItem> = _historyItems

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(true)
    var refreshSignal by mutableStateOf(0)
    override var shouldRestoreFocusToGrid by mutableStateOf(false)

    private var lastMax: Long = 0
    private var lastViewAt: Long = 0
    private var lastBusiness: String? = null

    private val _scrollStates = mutableStateMapOf<Any, Pair<Int, Int>>()
    private val _focusStates = mutableStateMapOf<Any, Int>()

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadHistory()
    }

    fun loadHistory() {
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

                val url = if (lastMax == 0L) {
                    "https://api.bilibili.com/x/web-interface/history/cursor?ps=20"
                } else {
                    "https://api.bilibili.com/x/web-interface/history/cursor?ps=20&max=$lastMax&view_at=$lastViewAt&business=${lastBusiness ?: ""}"
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookie)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        val historyResponse = json.decodeFromString<HistoryResponse>(responseBody)
                        if (historyResponse.code == 0) {
                            historyResponse.data?.let { data ->
                                if (BuildConfig.DEBUG) {
                                    Log.d("HistoryViewModel", "Loaded ${data.list.size} history items")
                                }
                                data.list.forEach { item ->
                                    Log.d("HistoryViewModel", "历史记录: 标题=${item.title}, 作者=${item.author_name}, bvid=${item.history.bvid}, 观看时间=${item.view_at}, 进度=${item.progress}, business=${item.history.business}, oid=${item.history.oid}, epid=${item.history.epid}")
                                    if (item.author_name.isEmpty()) {
                                        Log.d("HistoryViewModel", "完整数据: $item")
                                    }
                                }
                                _historyItems.addAll(data.list)
                                lastMax = data.cursor.max
                                lastViewAt = data.cursor.view_at
                                lastBusiness = data.cursor.business
                                hasMore = data.list.size >= 20
                            }
                        } else {
                            error = historyResponse.message
                        }
                    }
                } else {
                    error = "HTTP error: ${response.code}"
                }
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Failed to load history", e)
                error = "加载失败: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun refresh() {
        _historyItems.clear()
        lastMax = 0
        lastViewAt = 0
        lastBusiness = null
        hasMore = true
        error = null
        updateScrollState(UserTabType.HISTORY, 0, 0)
        updateFocusedIndex(UserTabType.HISTORY, 0)
        refreshSignal++
        loadHistory()
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

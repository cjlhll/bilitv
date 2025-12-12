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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

// Data Models based on PGC_INDEX_API.md
@Serializable
data class PgcConditionResponse(
    val code: Int,
    val message: String,
    val data: PgcConditionData? = null
)

@Serializable
data class PgcConditionData(
    val filter: List<PgcFilterItem>,
    val order: List<PgcOrderItem>? = null
)

@Serializable
data class PgcFilterItem(
    val field: String,
    val name: String,
    val values: List<PgcFilterValue>
)

@Serializable
data class PgcFilterValue(
    val keyword: String,
    val name: String
)

@Serializable
data class PgcOrderItem(
    val field: String,
    val name: String,
    val sort: String
)

@Serializable
data class PgcResultResponse(
    val code: Int,
    val message: String,
    val data: PgcResultData? = null
)

@Serializable
data class PgcResultData(
    val has_next: Int,
    val list: List<PgcIndexItem>,
    val num: Int,
    val size: Int,
    val total: Int
)

@Serializable
data class PgcIndexItem(
    val media_id: Long = 0,
    val season_id: Long,
    val title: String,
    val cover: String,
    val is_finish: Int,
    val season_status: Int,
    val index_show: String? = null,
    val order: String? = null,
    val score: String? = null,
    val badge: String? = null,
    val badge_info: PgcBadgeInfo? = null
)

@Serializable
data class PgcBadgeInfo(
    val bg_color: String? = null,
    val bg_color_night: String? = null,
    val text: String? = null
)

class AnimeListViewModel : ViewModel() {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    var seasonType by mutableStateOf(1)
        private set

    var filters by mutableStateOf<List<PgcFilterItem>>(emptyList())
        private set
    
    var orders by mutableStateOf<List<PgcOrderItem>>(emptyList())
        private set

    var selectedFilters by mutableStateOf<Map<String, String>>(emptyMap())
        private set
        
    var selectedOrder by mutableStateOf<String>("3")

    var videos by mutableStateOf<List<PgcIndexItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set
        
    var error by mutableStateOf<String?>(null)
        private set
    
    var focusedIndex by mutableStateOf(-1)
    var firstVisibleItemIndex by mutableStateOf(0)
    var firstVisibleItemScrollOffset by mutableStateOf(0)
    var shouldRestoreFocusToGrid by mutableStateOf(false)

    private var page = 1
    private var hasNext = true
    private var isInitialized = false

    fun initWithSeasonType(type: Int) {
        if (isInitialized && seasonType == type) return
        seasonType = type
        isInitialized = true
        loadCondition()
    }

    fun loadCondition() {
        viewModelScope.launch {
            try {
                val url = "https://api.bilibili.com/pgc/season/index/condition?season_type=$seasonType&type=0"
                val request = Request.Builder().url(url).build()
                
                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val resp = json.decodeFromString<PgcConditionResponse>(body)
                        if (resp.code == 0 && resp.data != null) {
                            filters = resp.data.filter
                            orders = resp.data.order ?: emptyList()
                            
                            Log.d("AnimeListVM", "======= 筛选条件API返回数据 =======")
                            Log.d("AnimeListVM", "season_type: $seasonType")
                            Log.d("AnimeListVM", "筛选条件数量: ${filters.size}")
                            filters.forEachIndexed { index, filter ->
                                Log.d("AnimeListVM", "[$index] field: ${filter.field}, name: ${filter.name}, 选项数: ${filter.values.size}")
                                filter.values.forEach { value ->
                                    Log.d("AnimeListVM", "  - keyword: ${value.keyword}, name: ${value.name}")
                                }
                            }
                            Log.d("AnimeListVM", "排序方式数量: ${orders.size}")
                            orders.forEach { order ->
                                Log.d("AnimeListVM", "  排序: field=${order.field}, name=${order.name}")
                            }
                            Log.d("AnimeListVM", "============================")
                            
                            val initialFilters = mutableMapOf<String, String>()
                            resp.data.filter.forEach { filter ->
                                if (filter.values.isNotEmpty()) {
                                    if (seasonType == 4 && filter.field == "area") {
                                        val guochanValue = filter.values.find { it.name.contains("国") || it.name.contains("中国") }
                                        if (guochanValue != null) {
                                            initialFilters[filter.field] = guochanValue.keyword
                                            Log.d("AnimeListVM", "国创列表：地区默认设置为 ${guochanValue.name} (keyword=${guochanValue.keyword})")
                                        } else {
                                            initialFilters[filter.field] = filter.values[0].keyword
                                        }
                                    } else {
                                        initialFilters[filter.field] = filter.values[0].keyword
                                    }
                                }
                            }
                            selectedFilters = initialFilters
                            
                            if (!orders.isNullOrEmpty()) {
                                selectedOrder = orders[0].field
                            }

                            loadResults(reset = true)
                        } else {
                            error = "Condition API Error: ${resp.message}"
                        }
                    }
                } else {
                    error = "Condition HTTP Error: ${response.code}"
                }
            } catch (e: Exception) {
                error = "Condition Network Error: ${e.message}"
            }
        }
    }

    fun updateFilter(field: String, keyword: String) {
        val newFilters = selectedFilters.toMutableMap()
        newFilters[field] = keyword
        selectedFilters = newFilters
        loadResults(reset = true)
    }
    
    fun updateOrder(orderField: String) {
        selectedOrder = orderField
        loadResults(reset = true)
    }

    fun loadResults(reset: Boolean = false) {
        if (isLoading && !reset) return
        if (reset) {
            page = 1
            hasNext = true
            videos = emptyList()
            // Reset focus when filters change
            focusedIndex = -1
            firstVisibleItemIndex = 0
            firstVisibleItemScrollOffset = 0
        }
        if (!hasNext) return

        isLoading = true
        error = null

        viewModelScope.launch {
            try {
                val params = StringBuilder("season_type=$seasonType&type=0&page=$page&pagesize=21")
                params.append("&order=$selectedOrder")
                
                selectedFilters.forEach { (field, keyword) ->
                    params.append("&$field=$keyword")
                }

                val url = "https://api.bilibili.com/pgc/season/index/result?${params}"
                val request = Request.Builder().url(url).build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val resp = json.decodeFromString<PgcResultResponse>(body)
                        if (resp.code == 0 && resp.data != null) {
                            val newVideos = resp.data.list
                            
                            if (BuildConfig.DEBUG) {
                                Log.d("AnimeList", "======= 番剧列表数据 =======")
                                Log.d("AnimeList", "总数: ${resp.data.total}, 当前页数量: ${newVideos.size}, 有下一页: ${resp.data.has_next == 1}")
                                newVideos.forEachIndexed { index, item ->
                                    Log.d("AnimeList", "[$index] ${item}")
                                    Log.d("AnimeList", "  season_id: ${item.season_id}, media_id: ${item.media_id}")
                                    Log.d("AnimeList", "  index_show: ${item.index_show}")
                                    Log.d("AnimeList", "  order: ${item.order}")
                                    Log.d("AnimeList", "  score: ${item.score}")
                                    Log.d("AnimeList", "  badge: ${item.badge}")
                                    Log.d("AnimeList", "  badge_info: bg_color=${item.badge_info?.bg_color}, text=${item.badge_info?.text}")
                                    Log.d("AnimeList", "  is_finish: ${item.is_finish}")
                                }
                                Log.d("AnimeList", "========================")
                            }
                            
                            videos = if (reset) newVideos else videos + newVideos
                            hasNext = resp.data.has_next == 1
                            if (hasNext) page++
                        } else {
                            error = "Result API Error: ${resp.message}"
                        }
                    }
                } else {
                    error = "Result HTTP Error: ${response.code}"
                }
            } catch (e: Exception) {
                error = "Result Network Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun loadNextPage() {
        if (hasNext && !isLoading) {
            loadResults(reset = false)
        }
    }
}

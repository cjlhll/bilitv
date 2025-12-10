package com.bili.bilitv
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bili.bilitv.BuildConfig
import com.bili.bilitv.utils.WbiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

@Serializable
data class SearchAllResponse(
    val code: Int,
    val message: String = "",
    val data: SearchAllData? = null
)

@Serializable
data class SearchAllData(
    val result: List<SearchAllResult>? = null,
    @SerialName("show_module_list") val showModuleList: List<String>? = null,
    val pageinfo: SearchAllPageInfo? = null
)

@Serializable
data class SearchAllResult(
    @SerialName("result_type") val resultType: String = "",
    val data: List<SearchAllItem>? = null
)

@Serializable
data class SearchAllItem(
    val title: String? = null,
    val name: String? = null,
    val bvid: String? = null,
    val aid: Long? = null,
    val pic: String? = null,
    val author: String? = null,
    val play: Long? = null,
    val danmaku: Long? = null,
    val duration: String? = null,
    val pubdate: Long? = null
)

@Serializable
data class SearchAllPageInfo(
    val video: SearchPageInfo? = null,
    @SerialName("bili_user") val biliUser: SearchPageInfo? = null,
    @SerialName("media_bangumi") val mediaBangumi: SearchPageInfo? = null,
    @SerialName("media_ft") val mediaFt: SearchPageInfo? = null
)

@Serializable
data class SearchPageInfo(
    @SerialName("numPages") val numPages: Int = 1,
    val page: Int = 1,
    @SerialName("numResults") val numResults: Int = 0
)

@Serializable
data class SearchTypeResponse(
    val code: Int,
    val message: String = "",
    val data: SearchTypeData? = null
)

@Serializable
data class SearchTypeData(
    val result: List<SearchVideoItem>? = null,
    @SerialName("numPages") val numPages: Int? = null,
    val page: Int? = null
)

@Serializable
data class SearchVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val author: String = "",
    val play: Long = 0,
    val danmaku: Long = 0,
    val duration: String = "",
    val pubdate: Long? = null,
    val pic: String = ""
)

@Serializable
data class BiliUserResult(
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = "",
    val fans: Long = 0,
    val videos: Int = 0
)

@Serializable
data class HotSearchResponse(
    val code: Int,
    val message: String = "",
    val data: HotSearchData? = null
)

@Serializable
data class HotSearchData(
    val trending: HotSearchTrending? = null
)

@Serializable
data class HotSearchTrending(
    val list: List<HotSearchItem>? = null
)

@Serializable
data class HotSearchItem(
    val keyword: String = "",
    @SerialName("show_name") val showName: String = "",
    val icon: String = "",
    val uri: String = "",
    val goto: String = ""
)

@Serializable
data class SuggestResponse(
    val code: Int,
    val result: SuggestResult? = null
)

@Serializable
data class SuggestResult(
    val tag: List<SuggestItem>? = null
)

@Serializable
data class SuggestItem(
    val value: String = "",
    val term: String = "",
    val name: String = ""
)

class SearchViewModel : ViewModel(), VideoGridStateManager {
    var videoResults by mutableStateOf<List<Video>>(emptyList())
    var userResults by mutableStateOf<List<BiliUserResult>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var showModules by mutableStateOf<List<String>>(emptyList())
    var currentKeyword by mutableStateOf("")
    var currentSearchType by mutableStateOf("video") // video / live / bili_user
    var currentOrder by mutableStateOf("totalrank") // totalrank / click / pubdate / dm / stow
    var currentPage by mutableStateOf(1)
    var totalPages by mutableStateOf(1)
    var availableTypes by mutableStateOf(listOf("video"))
    var hotSearches by mutableStateOf<List<HotSearchItem>>(emptyList())
    var isLoadingHotSearches by mutableStateOf(false)
    var hotSearchError by mutableStateOf<String?>(null)
    var searchSuggestions by mutableStateOf<List<SuggestItem>>(emptyList())
    var isLoadingSuggest by mutableStateOf(false)
    var suggestError by mutableStateOf<String?>(null)
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var searchInput by mutableStateOf("")
    var lastFocusArea by mutableStateOf("input")
    var lastFocusIndex by mutableStateOf(0)

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var cookie: String? = null
    private var imgKey: String? = null
    private var subKey: String? = null
    private var suggestRequestId = 0L
    private var searchRequestId = 0L
    private var currentSearchJob: Job? = null
    private var currentSuggestJob: Job? = null
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val logTag = "SearchVM"

    // 网格状态持久化
    private val scrollStateMap = mutableStateMapOf<Any, Pair<Int, Int>>()
    private val focusIndexMap = mutableStateMapOf<Any, Int>()
    override var shouldRestoreFocusToGrid by mutableStateOf(false)

    init {
        searchHistory = SearchHistoryManager.load()
    }

    fun updateSearchInput(text: String) {
        searchInput = text
    }

    fun clearSearchInput() {
        searchInput = ""
    }

    fun appendToSearchInput(text: String) {
        searchInput += text
    }

    fun deleteLastChar() {
        if (searchInput.isNotEmpty()) {
            searchInput = searchInput.dropLast(1)
        }
    }

    fun addHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        val newList = listOf(trimmed) + searchHistory.filterNot { it.equals(trimmed, ignoreCase = true) }
        searchHistory = newList.take(20)
        SearchHistoryManager.save(searchHistory)
    }

    fun updateFocus(area: String, index: Int = 0) {
        lastFocusArea = area
        lastFocusIndex = index
    }

        fun updateSearchOrder(order: String) {

            currentOrder = order

        }

    

        fun resetSearchState() {

            videoResults = emptyList()

            userResults = emptyList()

            availableTypes = listOf("video")

            showModules = emptyList()

            currentKeyword = ""

            currentPage = 1

            totalPages = 1

            error = null

        }

    

        fun searchWithOrder(keyword: String, type: String = currentSearchType, order: String = currentOrder, needGlobalInfo: Boolean = true) {

            if (keyword.isBlank()) return

            // 取消之前的搜索请求

            currentSearchJob?.cancel()

            val requestId = ++searchRequestId

            currentSearchJob = viewModelScope.launch {

                val wasLoading = isLoading

                isLoading = true

                error = null

                currentKeyword = keyword

                currentSearchType = type

                currentOrder = order

                currentPage = 1

                totalPages = 1

                

                // Always clear results to prevent showing stale data from previous tab/request

                videoResults = emptyList()

                userResults = emptyList()

    

                try {

                    Log.d(logTag, "searchWithOrder start keyword=$keyword type=$type order=$order requestId=$requestId global=$needGlobalInfo")

                    ensureCookie()

                    ensureWbiKeys()

                    

                    if (needGlobalInfo) {

                        fetchAllSearch(keyword)

                        if (searchRequestId != requestId) {

                            Log.d(logTag, "searchWithOrder cancelled after global fetch, newer request started requestId=$requestId")

                            return@launch

                        }

                    }

    

                    val activeType = currentSearchType

                    // Ensure we are fetching for the type that is currently active

                    if (activeType != type) {

                         Log.d(logTag, "searchWithOrder type mismatch, active=$activeType requested=$type")

                         return@launch

                    }

    

                    val activeOrder = currentOrder

                    val firstPage = fetchTypeSearch(1, activeType, activeOrder, requestId)

                    

                    if (searchRequestId != requestId) {

                        Log.d(logTag, "searchWithOrder cancelled after fetch, newer request started requestId=$requestId")

                        return@launch

                    }

                    

                    // Final check: ensure the type we fetched is still the current type

                    if (currentSearchType != activeType) {

                        Log.d(logTag, "searchWithOrder ignored result, type changed during fetch. current=$currentSearchType fetched=$activeType")

                        return@launch

                    }

    

                    if (activeType == "bili_user") {

                        videoResults = emptyList()

                    } else {

                        videoResults = firstPage

                    }

                    Log.d(logTag, "searchWithOrder completed keyword=$keyword type=$type order=$order requestId=$requestId")

                } catch (e: Exception) {

                    if (searchRequestId == requestId) {

                        Log.e(logTag, "searchWithOrder error", e)

                        error = e.localizedMessage ?: "搜索失败"

                    }

                } finally {

                    if (searchRequestId == requestId) {

                        isLoading = false

                    }

                    // 协程结束时清除Job引用

                    if (currentSearchJob?.isActive != true) {

                        currentSearchJob = null

                    }

                }

            }

        }

    fun search(keyword: String, type: String = currentSearchType) {
        // Reuse searchWithOrder for unified logic
        searchWithOrder(keyword, type, currentOrder, needGlobalInfo = true)
    }

    fun switchType(type: String) {
        if (type == currentSearchType) return
        currentSearchType = type
        // 切换到非video tab时，强制使用totalrank排序
        if (type != "video") {
            currentOrder = "totalrank"
        }
        onTabChanged(type)
        if (currentKeyword.isNotBlank()) {
            // Switching tabs does not need to re-fetch global info (tabs list etc.)
            searchWithOrder(currentKeyword, type, currentOrder, needGlobalInfo = false)
        } else {
            // 即使没有关键词也要清空数据，避免显示上一个tab的数据
            videoResults = emptyList()
            userResults = emptyList()
            currentPage = 1
            totalPages = 1
        }
    }

    fun onTabChanged(@Suppress("UNUSED_PARAMETER") type: String) {
        shouldRestoreFocusToGrid = false
    }

    fun onEnterFullScreenFromResult() {
        shouldRestoreFocusToGrid = true
    }

    fun loadMoreVideos() {
        if (isLoadingMore || currentKeyword.isBlank()) return
        if (currentPage >= totalPages) return
        val nextPage = currentPage + 1
        viewModelScope.launch {
            isLoadingMore = true
            try {
                Log.d(logTag, "loadMore page=$nextPage type=$currentSearchType")
                ensureCookie()
                ensureWbiKeys()
                if (currentSearchType == "bili_user") {
                    fetchTypeSearch(nextPage, currentSearchType, currentOrder, 0L, true)
                    currentPage = nextPage
                    return@launch
                }
                val more = fetchTypeSearch(nextPage, currentSearchType, currentOrder, 0L, true)
                if (more.isNotEmpty()) {
                    videoResults = videoResults + more
                }
                currentPage = nextPage
            } catch (e: Exception) {
                Log.e(logTag, "loadMore error", e)
                error = e.localizedMessage ?: "翻页失败"
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun loadHotSearches(force: Boolean = false) {
        if (isLoadingHotSearches) return
        if (hotSearches.isNotEmpty() && !force) return
        viewModelScope.launch {
            isLoadingHotSearches = true
            hotSearchError = null
            try {
                ensureCookie()
                ensureWbiKeys()
                hotSearches = fetchHotSearches()
            } catch (e: Exception) {
                Log.e(logTag, "hot search error", e)
                hotSearchError = e.localizedMessage ?: "获取热搜失败"
            } finally {
                isLoadingHotSearches = false
            }
        }
    }

    fun requestSuggestions(keyword: String) {
        // 取消之前的建议请求
        currentSuggestJob?.cancel()
        val requestId = ++suggestRequestId
        if (keyword.isBlank()) {
            searchSuggestions = emptyList()
            suggestError = null
            isLoadingSuggest = false
            return
        }
        currentSuggestJob = viewModelScope.launch {
            isLoadingSuggest = true
            suggestError = null
            try {
                ensureCookie()
                val list = fetchSearchSuggestions(keyword)
                if (suggestRequestId == requestId) {
                    searchSuggestions = list
                }
            } catch (e: Exception) {
                if (suggestRequestId == requestId) {
                    suggestError = e.localizedMessage ?: "获取搜索建议失败"
                    searchSuggestions = emptyList()
                }
            } finally {
                if (suggestRequestId == requestId) {
                    isLoadingSuggest = false
                }
                // 协程结束时清除Job引用
                if (currentSuggestJob?.isActive != true) {
                    currentSuggestJob = null
                }
            }
        }
    }

    private suspend fun fetchAllSearch(keyword: String, allowRetry: Boolean = true) {
        val params = mapOf("keyword" to keyword, "page" to "1")
        val url = buildSignedUrl("https://api.bilibili.com/x/web-interface/wbi/search/all/v2", params)
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent)
            cookie?.let { builder.addHeader("Cookie", it) }
            httpClient.newCall(builder.build()).execute().use { resp ->
                if (resp.code == 412) {
                    cookie = null
                    if (allowRetry) {
                        ensureCookie()
                        ensureWbiKeys()
                        fetchAllSearch(keyword, false)
                        return@use
                    } else {
                        throw IllegalStateException("请求被拦截(-412)，已重置Cookie")
                    }
                }
                if (!resp.isSuccessful) {
                    Log.e(logTag, "searchAll http error code=${resp.code}")
                    throw IllegalStateException("综合搜索HTTP错误${resp.code}")
                }
                val body = resp.body?.string() ?: return@use
                val apiResp = json.decodeFromString<SearchAllResponse>(body)
                if (apiResp.code != 0) {
                    Log.e(logTag, "searchAll api error code=${apiResp.code} msg=${apiResp.message}")
                    throw IllegalStateException(apiResp.message.ifEmpty { "综合搜索失败" })
                }
                if (BuildConfig.DEBUG) {
                    logSearchAllResults(apiResp)
                }
                showModules = apiResp.data?.showModuleList ?: emptyList()
                availableTypes = extractAvailableTypes(body)
                if (currentSearchType !in availableTypes) {
                    currentSearchType = availableTypes.firstOrNull() ?: "video"
                    Log.d(logTag, "current type missing in pageinfo, fallback to $currentSearchType")
                }
                val pageInfo = apiResp.data?.pageinfo
                totalPages = when (currentSearchType) {
                    "bili_user" -> pageInfo?.biliUser?.numPages ?: 1
                    "media_bangumi" -> pageInfo?.mediaBangumi?.numPages ?: 1
                    "media_ft" -> pageInfo?.mediaFt?.numPages ?: 1
                    else -> pageInfo?.video?.numPages ?: 1
                }.coerceAtLeast(1)
                Log.d(logTag, "searchAll ok availableTypes=${availableTypes.joinToString()} modules=${showModules.joinToString()} pages=$totalPages")
            }
        }
    }

    private suspend fun fetchTypeSearch(page: Int, type: String, order: String = currentOrder, requestId: Long = 0L, allowRetry: Boolean = true): List<Video> {
        val params = mapOf(
            "search_type" to type,
            "keyword" to currentKeyword,
            "page" to page.toString(),
            "order" to order,
            "duration" to "0",
            "tids" to "0"
        )
        val url = buildSignedUrl("https://api.bilibili.com/x/web-interface/wbi/search/type", params)
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent)
                .addHeader("Referer", "https://www.bilibili.com")
            cookie?.let { builder.addHeader("Cookie", it) }
            httpClient.newCall(builder.build()).execute().use { resp ->
                if (resp.code == 412) {
                    cookie = null
                    if (allowRetry) {
                        ensureCookie()
                        ensureWbiKeys()
                        return@withContext fetchTypeSearch(page, type, order, requestId, false)
                    } else {
                        throw IllegalStateException("请求被拦截(-412)，已重置Cookie")
                    }
                }
                if (!resp.isSuccessful) {
                    Log.e(logTag, "searchType http error code=${resp.code} type=$type")
                    throw IllegalStateException("分类搜索HTTP错误${resp.code}")
                }
                val body = resp.body?.string() ?: return@use emptyList()
                if (requestId > 0 && searchRequestId != requestId) {
                    Log.d(logTag, "fetchTypeSearch cancelled, requestId mismatch current=$searchRequestId requested=$requestId")
                    return@use emptyList()
                }
                val mapped = if (type == "video") {
                    val apiResp = json.decodeFromString<SearchTypeResponse>(body)
                    if (apiResp.code != 0) {
                        Log.e(logTag, "searchType api error code=${apiResp.code} msg=${apiResp.message} type=$type")
                        throw IllegalStateException(apiResp.message.ifEmpty { "分类搜索失败" })
                    }
                    apiResp.data?.numPages?.let { if (it > 0) totalPages = it }
                    val list = apiResp.data?.result ?: emptyList()
                    list.map { it.toVideo() }
                } else {
                    parseNonVideoResults(body, type, page, requestId)
                }
                Log.d(logTag, "searchType ok page=$page mapped=${mapped.size} totalPages=$totalPages type=$type")
                return@use mapped
            } ?: emptyList()
        }
    }

    private suspend fun fetchHotSearches(allowRetry: Boolean = true): List<HotSearchItem> {
        val params = mapOf("limit" to "20", "platform" to "web")
        val url = buildSignedUrl("https://api.bilibili.com/x/web-interface/wbi/search/square", params)
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent)
            cookie?.let { builder.addHeader("Cookie", it) }
            httpClient.newCall(builder.build()).execute().use { resp ->
                if (resp.code == 412) {
                    cookie = null
                    if (allowRetry) {
                        ensureCookie()
                        ensureWbiKeys()
                        return@withContext fetchHotSearches(false)
                    } else {
                        throw IllegalStateException("请求被拦截(-412)，已重置Cookie")
                    }
                }
                if (!resp.isSuccessful) {
                    Log.e(logTag, "hot search http error code=${resp.code}")
                    throw IllegalStateException("热搜HTTP错误${resp.code}")
                }
                val body = resp.body?.string() ?: return@use emptyList()
                val apiResp = json.decodeFromString<HotSearchResponse>(body)
                if (apiResp.code != 0) {
                    Log.e(logTag, "hot search api error code=${apiResp.code} msg=${apiResp.message}")
                    throw IllegalStateException(apiResp.message.ifEmpty { "获取热搜失败" })
                }
                val list = apiResp.data?.trending?.list.orEmpty()
                return@use list.map { item ->
                    val name = item.showName.ifBlank { item.keyword }
                    val icon = if (item.icon.startsWith("//")) "https:${item.icon}" else item.icon
                    item.copy(showName = name, icon = icon)
                }.filter { it.showName.isNotBlank() || it.keyword.isNotBlank() }
            } ?: emptyList()
        }
    }

    private suspend fun fetchSearchSuggestions(keyword: String, allowRetry: Boolean = true): List<SuggestItem> {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("s.search.bilibili.com")
            .addPathSegments("main/suggest")
            .addQueryParameter("term", keyword)
            .addQueryParameter("main_ver", "v1")
            .addQueryParameter("func", "suggest")
            .addQueryParameter("suggest_type", "accurate")
            .addQueryParameter("sub_type", "tag")
            .addQueryParameter("tag_num", "10")
            .build()
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent)
            cookie?.let { builder.addHeader("Cookie", it) }
            httpClient.newCall(builder.build()).execute().use { resp ->
                if (resp.code == 412) {
                    cookie = null
                    if (allowRetry) {
                        ensureCookie()
                        return@withContext fetchSearchSuggestions(keyword, false)
                    } else {
                        throw IllegalStateException("请求被拦截(-412)，已重置Cookie")
                    }
                }
                if (!resp.isSuccessful) {
                    throw IllegalStateException("搜索建议HTTP错误${resp.code}")
                }
                val body = resp.body?.string() ?: return@use emptyList()
                val apiResp = json.decodeFromString<SuggestResponse>(body)
                if (apiResp.code != 0) {
                    throw IllegalStateException("搜索建议失败")
                }
                return@use apiResp.result?.tag.orEmpty().map {
                    it.copy(name = stripTags(it.name))
                }
            } ?: emptyList()
        }
    }

    private suspend fun ensureCookie() {
        if (cookie?.contains("buvid3") == true) return
        withContext(Dispatchers.IO) {
            val cookieMap = mutableMapOf<String, String>()
            SessionManager.getCookieString()?.let { cookieMap.putAll(parseCookieString(it)) }
            if (!cookieMap.containsKey("buvid3")) {
                httpClient.newCall(
                    Request.Builder()
                        .url("https://www.bilibili.com/")
                        .addHeader("User-Agent", userAgent)
                        .build()
                ).execute().use { resp ->
                    resp.headers("Set-Cookie").forEach { line ->
                        val pair = line.substringBefore(";")
                        val keyValue = pair.split("=", limit = 2)
                        if (keyValue.size == 2) {
                            cookieMap[keyValue[0]] = keyValue[1]
                        }
                    }
                }
            }
            cookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            Log.d(logTag, "cookie ready hasBuvid=${cookie?.contains("buvid3") == true}")
        }
    }

    private suspend fun ensureWbiKeys() {
        if (imgKey != null && subKey != null) return
        if (SessionManager.wbiImgKey != null && SessionManager.wbiSubKey != null) {
            imgKey = SessionManager.wbiImgKey
            subKey = SessionManager.wbiSubKey
            return
        }
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/nav")
                .addHeader("User-Agent", userAgent)
            cookie?.let { builder.addHeader("Cookie", it) }
            httpClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string() ?: return@use
                val nav = json.decodeFromString<NavResponse>(body)
                nav.data?.wbi_img?.let { img ->
                    imgKey = img.img_url.substringAfterLast("/").substringBefore(".")
                    subKey = img.sub_url.substringAfterLast("/").substringBefore(".")
                }
            }
            if (imgKey == null || subKey == null) {
                throw IllegalStateException("缺少WBI密钥")
            }
            Log.d(logTag, "wbi ok imgKey=$imgKey subKey=$subKey")
        }
    }

    private fun buildSignedUrl(base: String, params: Map<String, String>): String {
        val iKey = imgKey ?: throw IllegalStateException("缺少WBI密钥")
        val sKey = subKey ?: throw IllegalStateException("缺少WBI密钥")
        val signed = WbiUtil.sign(params, iKey, sKey)
        val query = signed.entries.joinToString("&") { "${it.key}=${WbiUtil.encodeURIComponent(it.value)}" }
        return "$base?$query"
    }

    private fun parseCookieString(cookie: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        cookie.split(";").forEach { part ->
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2 && kv[0].isNotBlank()) {
                map[kv[0]] = kv[1]
            }
        }
        return map
    }

    private fun SearchVideoItem.toVideo(): Video {
        val durationSeconds = parseDurationToSeconds(duration)
        return Video(
            id = bvid.ifEmpty { aid.toString() },
            aid = aid,
            bvid = bvid,
            cid = cid,
            title = stripTags(title),
            coverUrl = normalizeCover(pic),
            author = author,
            playCount = play.toString(),
            danmakuCount = danmaku.toString(),
            duration = if (duration.contains(":")) duration else formatDuration(durationSeconds),
            durationSeconds = durationSeconds,
            pubDate = pubdate
        )
    }

    private fun SearchVideoItem.toLiveVideo(): Video {
        // live result uses cid as roomid sometimes; reuse fields where possible
        val cover = normalizeCover(pic)
        return Video(
            id = (cid.takeIf { it != 0L } ?: aid).toString(),
            aid = aid,
            bvid = bvid,
            cid = cid,
            title = stripTags(title),
            coverUrl = cover,
            author = author,
            playCount = play.toString(),
            danmakuCount = danmaku.toString(),
            duration = "",
            durationSeconds = 0,
            pubDate = pubdate
        )
    }

    private fun SearchVideoItem.toUserVideo(): Video {
        // user search返回 uname/usign/upic 等，当前字段名复用 author/title
        val name = if (title.isNotBlank()) title else author
        val face = normalizeCover(pic)
        return Video(
            id = bvid.ifEmpty { aid.toString() },
            aid = aid,
            bvid = bvid,
            cid = cid,
            title = stripTags(name),
            coverUrl = face,
            author = author,
            playCount = play.toString(),
            danmakuCount = danmaku.toString(),
            duration = "",
            durationSeconds = 0,
            pubDate = pubdate
        )
    }

    private fun parseNonVideoResults(body: String, type: String, page: Int, requestId: Long = 0L): List<Video> {
        val root = json.parseToJsonElement(body)
        val dataObj = (root as? JsonObject)?.get("data") as? JsonObject ?: return emptyList()
        dataObj["numPages"]?.jsonPrimitive?.content?.toIntOrNull()?.let { if (it > 0) totalPages = it }
        if (type == "bili_user") {
            if (requestId > 0 && searchRequestId != requestId) {
                Log.d(logTag, "parseNonVideoResults cancelled for users, requestId mismatch current=$searchRequestId requested=$requestId")
                return emptyList()
            }
            val users = parseUserResults(dataObj)
            userResults = if (page == 1) users else userResults + users
            return emptyList()
        }
        val resultElem = dataObj["result"] ?: return emptyList()
        val videos = mutableListOf<Video>()
        when (resultElem) {
            is JsonArray -> {
                resultElem.forEach { item ->
                    (item as? JsonObject)?.let { obj ->
                        videos.add(mapJsonItemToVideo(obj, type))
                    }
                }
            }
            is JsonObject -> {
                // 优先使用与当前类型同名的键，否则遍历所有数组
                val preferred = resultElem[type]
                if (preferred is JsonArray) {
                    preferred.forEach { item ->
                        (item as? JsonObject)?.let { obj ->
                            videos.add(mapJsonItemToVideo(obj, type))
                        }
                    }
                } else {
                    resultElem.values.forEach { elem ->
                        if (elem is JsonArray) {
                            elem.forEach { item ->
                                (item as? JsonObject)?.let { obj ->
                                    videos.add(mapJsonItemToVideo(obj, type))
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
        return videos
    }

    private fun parseUserResults(dataObj: JsonObject): List<BiliUserResult> {
        val resultElem = dataObj["result"] ?: return emptyList()
        val users = mutableListOf<BiliUserResult>()
        fun addFrom(obj: JsonObject) {
            val mid = obj.longOrZero("mid")
            val uname = stripTags(
                obj.stringOrEmpty("uname")
                    .ifBlank { obj.stringOrEmpty("title") }
                    .ifBlank { obj.stringOrEmpty("name") }
            )
            val face = normalizeCover(
                obj.stringOrEmpty("upic")
                    .ifBlank { obj.stringOrEmpty("face") }
                    .ifBlank { obj.stringOrEmpty("pic") }
            )
            val sign = obj.stringOrEmpty("usign")
            val fans = obj.stringOrNumber("fans").toLongOrNull() ?: 0L
            val videos = obj.longOrZero("videos").toInt()
            users.add(
                BiliUserResult(
                    mid = mid,
                    uname = uname,
                    face = face,
                    sign = sign,
                    fans = fans,
                    videos = videos
                )
            )
        }
        when (resultElem) {
            is JsonArray -> {
                resultElem.forEach { item ->
                    (item as? JsonObject)?.let { addFrom(it) }
                }
            }
            is JsonObject -> {
                resultElem.values.forEach { elem ->
                    if (elem is JsonArray) {
                        elem.forEach { item ->
                            (item as? JsonObject)?.let { addFrom(it) }
                        }
                    }
                }
            }
            else -> {}
        }
        return users
    }

    private fun isLiveResultKey(key: String): Boolean {
        return key.contains("live_room") || key == "live" || key == "live_room_v2"
    }

    private fun isUserResultKey(key: String): Boolean {
        return key.contains("user")
    }

    private fun mapJsonItemToVideo(obj: JsonObject, type: String): Video {
        val bvid = obj.stringOrEmpty("bvid")
        val aid = obj.longOrZero("aid")
        val seasonId = obj.longOrZero("season_id")
        val mediaId = obj.longOrZero("media_id")
        val idField = obj.longOrZero("id")
        val mid = obj.longOrZero("mid")
        val roomId = obj.longOrZero("roomid").takeIf { it != 0L } ?: obj.longOrZero("cid")

        val id = when {
            bvid.isNotBlank() -> bvid
            aid != 0L -> aid.toString()
            seasonId != 0L -> seasonId.toString()
            mediaId != 0L -> mediaId.toString()
            roomId != 0L -> roomId.toString()
            idField != 0L -> idField.toString()
            mid != 0L -> mid.toString()
            else -> "item_${hashCode()}"
        }

        val title = stripTags(
            obj.stringOrEmpty("title")
                .ifBlank { obj.stringOrEmpty("name") }
                .ifBlank { obj.stringOrEmpty("uname") }
        )
        val author = obj.stringOrEmpty("author")
            .ifBlank { obj.stringOrEmpty("uname") }
            .ifBlank { obj.stringOrEmpty("name") }
        val cover = normalizeCover(
            obj.stringOrEmpty("cover")
                .ifBlank { obj.stringOrEmpty("pic") }
                .ifBlank { obj.stringOrEmpty("image") }
                .ifBlank { obj.stringOrEmpty("cover_url") }
                .ifBlank { obj.stringOrEmpty("face") }
        )
        val playCount = obj.stringOrNumber("play")
            .ifBlank { obj.stringOrNumber("view") }
            .ifBlank { obj.stringOrNumber("online") }
        val danmaku = obj.stringOrNumber("danmaku")
        val durationStr = obj.stringOrEmpty("duration")
        val durationSeconds = if (durationStr.contains(":")) parseDurationToSeconds(durationStr) else obj.longOrZero("duration")
        val duration = if (durationStr.isNotBlank()) durationStr else formatDuration(durationSeconds)
        val pubDate = obj.longOrZero("pubdate").takeIf { it != 0L }
        val desc = obj.stringOrEmpty("desc").ifBlank { obj.stringOrEmpty("evaluate") }
        val badges = obj.parseBadges()
        val epSize = obj.longOrZero("ep_size").toInt()
        val mediaScoreObj = obj["media_score"] as? JsonObject
        val score = mediaScoreObj?.get("score")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val scoreUsers = mediaScoreObj?.get("user_count")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val mediaType = obj.longOrZero("media_type").toInt()
        val seasonType = obj.longOrZero("season_type").toInt()
        val seasonTypeName = obj.stringOrEmpty("season_type_name")
        val url = obj.stringOrEmpty("url")
        val buttonText = obj.stringOrEmpty("button_text")
        val isFollow = obj.longOrZero("is_follow") == 1L
        val selectionStyle = obj.stringOrEmpty("selection_style")
        val orgTitle = stripTags(obj.stringOrEmpty("org_title"))
        val cv = obj.stringOrEmpty("cv")
        val staff = obj.stringOrEmpty("staff")
        val episodes = parseEpisodes(obj["eps"])

        return Video(
            id = id,
            aid = aid,
            bvid = bvid,
            cid = roomId,
            title = title.ifBlank { author },
            coverUrl = cover,
            author = author,
            playCount = playCount,
            danmakuCount = danmaku,
            duration = duration,
            durationSeconds = durationSeconds,
            pubDate = pubDate,
            desc = desc,
            badges = badges,
            epSize = epSize,
            mediaScore = score,
            mediaScoreUsers = scoreUsers,
            mediaType = mediaType,
            seasonId = seasonId,
            mediaId = mediaId,
            seasonType = seasonType,
            seasonTypeName = seasonTypeName,
            url = url,
            buttonText = buttonText,
            isFollow = isFollow,
            selectionStyle = selectionStyle,
            orgTitle = orgTitle,
            cv = cv,
            staff = staff,
            episodes = episodes
        )
    }

    private fun JsonObject.stringOrEmpty(key: String): String {
        return this[key]?.jsonPrimitive?.content ?: ""
    }

    private fun JsonObject.longOrZero(key: String): Long {
        return this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
    }

    private fun JsonObject.stringOrNumber(key: String): String {
        val prim = this[key]?.jsonPrimitive ?: return ""
        val num = prim.content.toLongOrNull()
        return if (prim.content.isNotBlank()) prim.content else num?.toString() ?: ""
    }

    private fun JsonObject.parseBadges(): List<Badge> {
        val arr = this["badges"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            (el as? JsonObject)?.let { obj ->
                Badge(
                    text = obj.stringOrEmpty("text"),
                    textColor = obj.stringOrEmpty("text_color"),
                    textColorNight = obj.stringOrEmpty("text_color_night"),
                    bgColor = obj.stringOrEmpty("bg_color"),
                    bgColorNight = obj.stringOrEmpty("bg_color_night"),
                    borderColor = obj.stringOrEmpty("border_color"),
                    borderColorNight = obj.stringOrEmpty("border_color_night"),
                    bgStyle = obj.longOrZero("bg_style").toInt()
                )
            }
        }.filter { it.text.isNotBlank() }
    }

    private fun parseEpisodes(element: JsonElement?): List<MediaEpisode> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { epEl ->
            (epEl as? JsonObject)?.let { epObj ->
                val badgeArray = epObj["badges"] as? JsonArray
                val badgeTexts = badgeArray?.mapNotNull { badgeEl ->
                    when (badgeEl) {
                        is JsonObject -> badgeEl.stringOrEmpty("text").ifBlank { null }
                        else -> runCatching { badgeEl.jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() }
                    }
                } ?: emptyList()

                MediaEpisode(
                    id = epObj.longOrZero("id"),
                    title = epObj.stringOrEmpty("title"),
                    longTitle = epObj.stringOrEmpty("long_title"),
                    indexTitle = epObj.stringOrEmpty("index_title"),
                    cover = normalizeCover(epObj.stringOrEmpty("cover")),
                    url = epObj.stringOrEmpty("url"),
                    releaseDate = epObj.stringOrEmpty("release_date"),
                    badges = badgeTexts
                )
            }
        }
    }

    private fun extractAvailableTypes(body: String): List<String> {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return listOf("video")
            val pageInfo = root["data"]?.jsonObject?.get("pageinfo")?.jsonObject ?: return listOf("video")
            val ordered = listOf("video", "media_bangumi", "media_ft", "live", "bili_user")
            val list = mutableListOf<String>()
            ordered.forEach { key ->
                val obj = pageInfo[key] as? JsonObject
                val total = obj?.get("total")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                if (total > 0) list.add(key)
            }
            if (list.isNotEmpty()) list else listOf("video")
        } catch (e: Exception) {
            Log.e(logTag, "extractAvailableTypes error", e)
            listOf("video")
        }
    }

    private fun logSearchAllResults(resp: SearchAllResponse) {
        val summary = resp.data?.result?.joinToString(" | ") { r ->
            val count = r.data?.size ?: 0
            val samples = r.data.orEmpty().take(3).joinToString(",") {
                it.title ?: it.name ?: ""
            }
            "${r.resultType}:$count[$samples]"
        } ?: "none"
        Log.d(logTag, "searchAll result summary => $summary")
    }

    private fun stripTags(text: String): String {
        return text.replace(Regex("<.*?>"), "")
    }

    private fun normalizeCover(pic: String): String {
        return if (pic.startsWith("//")) "https:$pic" else pic
    }

    private fun parseDurationToSeconds(text: String): Long {
        if (text.isBlank()) return 0
        val parts = text.split(":").filter { it.isNotBlank() }
        val numbers = parts.mapNotNull { it.toLongOrNull() }
        if (numbers.size == parts.size && numbers.isNotEmpty()) {
            return when (numbers.size) {
                3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
                2 -> numbers[0] * 60 + numbers[1]
                1 -> numbers[0]
                else -> 0
            }
        }
        return text.toLongOrNull() ?: 0
    }

    private fun formatDuration(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

    // VideoGridStateManager 实现
    override fun updateScrollState(key: Any, index: Int, offset: Int) {
        scrollStateMap[key] = index to offset
    }

    override fun getScrollState(key: Any): Pair<Int, Int> {
        return scrollStateMap[key] ?: (0 to 0)
    }

    override fun updateFocusedIndex(key: Any, index: Int) {
        focusIndexMap[key] = index
    }

    override fun getFocusedIndex(key: Any): Int {
        return focusIndexMap[key] ?: -1
    }
}


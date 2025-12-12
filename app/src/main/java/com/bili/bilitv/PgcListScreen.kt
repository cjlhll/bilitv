package com.bili.bilitv

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PgcListScreen(
    initialSeasonType: Int = 1,
    tabs: List<Pair<String, Int>> = emptyList(),
    viewModel: PgcListViewModel = viewModel(),
    onMediaClick: (Video) -> Unit,
    onBack: () -> Unit
) {
    // 建立season_type到index_type的映射关系
    val seasonTypeToIndexType = mapOf(
        1 to 102, // 全部
        2 to 2,   // 电影
        3 to 5,   // 电视剧
        4 to 3,   // 纪录片
        5 to 7    // 综艺
    )
    
    var currentSeasonType by remember(initialSeasonType) { mutableStateOf(initialSeasonType) }
    val currentIndexType = seasonTypeToIndexType[currentSeasonType] ?: 102

    LaunchedEffect(currentSeasonType) {
        viewModel.initWithSeasonType(currentSeasonType, currentIndexType)
    }

    BackHandler {
        onBack()
    }

    val videos = viewModel.videos
    val filters = viewModel.filters
    val orders = viewModel.orders
    val selectedFilters = viewModel.selectedFilters
    val selectedOrder = viewModel.selectedOrder
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    
    // State Manager adapter for the generic grid
    val stateManager = remember {
        object : VideoGridStateManager {
            override fun updateScrollState(key: Any, index: Int, offset: Int) {
                viewModel.firstVisibleItemIndex = index
                viewModel.firstVisibleItemScrollOffset = offset
            }

            override fun getScrollState(key: Any): Pair<Int, Int> {
                return viewModel.firstVisibleItemIndex to viewModel.firstVisibleItemScrollOffset
            }

            override fun updateFocusedIndex(key: Any, index: Int) {
                viewModel.focusedIndex = index
            }

            override fun getFocusedIndex(key: Any): Int {
                return viewModel.focusedIndex
            }

            override val shouldRestoreFocusToGrid: Boolean
                get() = viewModel.shouldRestoreFocusToGrid
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Optional Tabs
        if (tabs.isNotEmpty()) {
            CommonTabRow(
                tabs = tabs.map { pair -> TabItem(pair.second, pair.first) },
                selectedTab = currentSeasonType,
                onTabSelected = { seasonType ->
                    if (seasonType != currentSeasonType) {
                        Log.d("PgcListScreen", "切换tab：seasonType=$seasonType, indexType=${seasonTypeToIndexType[seasonType]}")
                        currentSeasonType = seasonType
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Filters Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Order Filter
            if (orders.isNotEmpty()) {
                val options = orders.map { FilterOption(it.name, it.field) }
                val currentOrderName = orders.find { it.field == selectedOrder }?.name ?: "排序"
                
                FilterSelectButton(
                    label = "排序",
                    selectedOptionLabel = currentOrderName,
                    options = options,
                    onOptionSelected = { option ->
                        viewModel.updateOrder(option.value)
                    }
                )
            }

            // Other Filters
            filters.forEach { filter ->
                if (filter.values.isNotEmpty()) {
                    val options = filter.values.map { FilterOption(it.name, it.keyword) }
                    val currentVal = selectedFilters[filter.field]
                    val currentName = filter.values.find { it.keyword == currentVal }?.name ?: filter.name
                    
                    FilterSelectButton(
                        label = filter.name,
                        selectedOptionLabel = currentName,
                        options = options,
                        onOptionSelected = { option ->
                            viewModel.updateFilter(filter.field, option.value)
                        }
                    )
                }
            }
        }

        if (isLoading && videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载失败: $error", color = MaterialTheme.colorScheme.error)
            }
        } else {
            // Convert PgcIndexItem to Video for the generic grid
            val videoItems = videos.map { pgcItem ->
                val epSize = pgcItem.index_show?.let { text ->
                    Regex("(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                } ?: 0

                val mediaScore = pgcItem.score?.let { text ->
                    text.replace("分", "").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                } ?: 0.0

                                val bottomText = pgcItem.index_show
                
                                val desc = "" // Clear desc as requested
                
                                Video(
                                    id = pgcItem.season_id.toString(),
                                    title = pgcItem.title,
                                    coverUrl = pgcItem.cover,
                                    author = "",
                                    playCount = "",
                                    desc = desc,
                                    seasonId = pgcItem.season_id,
                                    mediaId = pgcItem.media_id,
                                    isFollow = pgcItem.is_finish == 1,
                                    mediaScore = mediaScore,
                                    epSize = epSize,
                                    bottomText = bottomText,
                                    badges = pgcItem.badge?.let { 
                                         listOf(Badge(text = it, bgColor = pgcItem.badge_info?.bg_color ?: "", borderColor = "", textColor = ""))
                                    } ?: emptyList()
                                )
                            }
            CommonVideoGrid(
                videos = videoItems,
                stateManager = stateManager,
                stateKey = "PgcList_$currentSeasonType",
                columns = 5,
                onVideoClick = { video ->
                    viewModel.shouldRestoreFocusToGrid = true
                    onMediaClick(video)
                },
                onLoadMore = {
                     viewModel.loadNextPage()
                },
                horizontalSpacing = 12.dp,
                verticalSpacing = 12.dp,
                contentPadding = PaddingValues(bottom = 32.dp, start = 12.dp, end = 12.dp)
            ) { video, modifier ->
                // Use VerticalMediaCard for anime items as they are vertical posters
                VerticalMediaCard(
                    video = video,
                    onClick = {
                        viewModel.shouldRestoreFocusToGrid = true
                        onMediaClick(video)
                    },
                    modifier = modifier
                )
            }
        }
    }
}

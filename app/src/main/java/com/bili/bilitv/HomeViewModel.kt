package com.bili.bilitv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    var selectedTab by mutableStateOf(TabType.RECOMMEND)
    var hotVideos by mutableStateOf<List<VideoItemData>>(emptyList())

    // Store state per tab
    // Pair(index, offset)
    private val _tabScrollStates = mutableStateMapOf<TabType, Pair<Int, Int>>()
    // Focused index
    private val _tabFocusStates = mutableStateMapOf<TabType, Int>()

    fun updateScrollState(tab: TabType, index: Int, offset: Int) {
        _tabScrollStates[tab] = index to offset
    }

    fun updateFocusedIndex(tab: TabType, index: Int) {
        _tabFocusStates[tab] = index
    }
    
    fun getScrollState(tab: TabType): Pair<Int, Int> {
        return _tabScrollStates[tab] ?: (0 to 0)
    }
    
    fun getFocusedIndex(tab: TabType): Int {
        return _tabFocusStates[tab] ?: -1
    }
}

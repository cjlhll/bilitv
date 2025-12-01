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

    // Flag to control focus restoration logic
    // When switching tabs explicitly, we want focus to stay on the tab bar (false)
    // When returning from player or other screens, we want focus to restore to the list (true)
    var shouldRestoreFocusToGrid by mutableStateOf(false)

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
    
    fun onTabChanged(newTab: TabType) {
        selectedTab = newTab
        // Switch tab -> Don't restore focus to grid immediately (keep on tab)
        shouldRestoreFocusToGrid = false
    }
    
    fun onEnterFullScreen() {
        // Prepare to restore focus when coming back
        shouldRestoreFocusToGrid = true
    }
}

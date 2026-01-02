package com.bili.bilitv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 通用选项卡项数据类
 */
data class TabItem(
    val id: Any,           // 用作唯一标识符，可以是枚举、字符串或数字
    val title: String      // 显示的标题文本
)

/**
 * 通用选项卡按钮组件
 *
 * @param text 按钮显示的文本
 * @param selected 是否被选中
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun CommonTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                          else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
        modifier = modifier
            .width(68.dp)
            .height(26.dp)
            .onFocusChanged { isFocused = it.isFocused },
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * 通用选项卡行组件（水平排列的选项卡）
 *
 * @param tabs 选项卡项列表
 * @param selectedTab 当前选中的选项卡
 * @param onTabSelected 选项卡选择回调
 * @param modifier 修饰符
 * @param contentPadding 内容边距
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> CommonTabRow(
    tabs: List<TabItem>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
) {
    val selectedTabIndex = tabs.indexOfFirst { it.id.toString() == selectedTab.toString() }.coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedTabIndex,
        separator = { Spacer(modifier = Modifier.width(8.dp)) },
        indicator = { tabPositions, doesTabRowHaveFocus ->
            TabRowDefaults.PillIndicator(
                currentTabPosition = tabPositions[selectedTabIndex],
                doesTabRowHaveFocus = doesTabRowHaveFocus,
                activeColor = Color.White,
                inactiveColor = Color.White.copy(alpha = 0.5f)
            )
        },
        modifier = modifier
            .padding(contentPadding)
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedTabIndex,
                onFocus = {
                    if (index != selectedTabIndex) {
                        @Suppress("UNCHECKED_CAST")
                        onTabSelected(tab.id as T)
                    }
                },
                onClick = {
                    @Suppress("UNCHECKED_CAST")
                    onTabSelected(tab.id as T)
                }
            ) {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == selectedTabIndex) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 简化的通用选项卡行组件，适用于枚举类型的选项卡
 *
 * @param tabs 选项卡项枚举类
 * @param selectedTab 当前选中的选项卡
 * @param onTabSelected 选项卡选择回调
 * @param modifier 修饰符
 * @param contentPadding 内容边距
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T : Enum<T>> CommonTabRowWithEnum(
    tabs: Array<T>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
) {
    val selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedTabIndex,
        separator = { Spacer(modifier = Modifier.width(8.dp)) },
        indicator = { tabPositions, doesTabRowHaveFocus ->
            TabRowDefaults.PillIndicator(
                currentTabPosition = tabPositions[selectedTabIndex],
                doesTabRowHaveFocus = doesTabRowHaveFocus,
                activeColor = Color.White,
                inactiveColor = Color.White.copy(alpha = 0.5f)
            )
        },
        modifier = modifier
            .padding(contentPadding)
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedTabIndex,
                onFocus = {
                    if (index != selectedTabIndex) {
                        onTabSelected(tab)
                    }
                },
                onClick = { onTabSelected(tab) }
            ) {
                Text(
                    text = tab.getTitle(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == selectedTabIndex) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 日期标签栏组件 - TV版（紧凑设计）
 * 包含最近更新和周一到周日共8个选项
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DateTabBarForTV(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "新番更新表",
    lazyListState: LazyListState = rememberLazyListState(), // Keep for compatibility though TabRow might not use it directly
    focusedIndex: Int = -1,
    shouldRestoreFocus: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    // 生成8个日期选项：最近更新 + 周一到周日
    val tabItems = remember {
        (listOf("最近更新") + listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"))
    }

    Row(
        modifier = modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标题
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 16.dp)
        )
        
        TabRow(
            selectedTabIndex = selectedIndex,
            separator = { Spacer(modifier = Modifier.width(8.dp)) },
            indicator = { tabPositions, doesTabRowHaveFocus ->
                TabRowDefaults.PillIndicator(
                    currentTabPosition = tabPositions[selectedIndex],
                    doesTabRowHaveFocus = doesTabRowHaveFocus,
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.5f)
                )
            },
            modifier = Modifier.wrapContentWidth()
        ) {
            tabItems.forEachIndexed { index, tabTitle ->
                val focusRequester = remember { FocusRequester() }
                
                LaunchedEffect(shouldRestoreFocus) {
                    if (shouldRestoreFocus && focusedIndex == index) {
                        delay(100)
                        focusRequester.requestFocus()
                    }
                }

                Tab(
                    selected = index == selectedIndex,
                    onFocus = {
                        if (index != selectedIndex) {
                            onTabSelected(index)
                        }
                    },
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    Text(
                        text = tabTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == selectedIndex) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * 扩展函数，为枚举类型提供自定义标题
 */
fun <T : Enum<T>> T.getTitle(): String {
    return when (this) {
        is TabType -> this.title
        is UserTabType -> this.title
        else -> this.name
    }
}

// 预览函数
@Preview(showBackground = true)
@Composable
fun DateTabBarForTVPreview() {
    var selectedIndex by remember { mutableStateOf(0) }
    
    BiliTVTheme {
        Surface {
            DateTabBarForTV(
                selectedIndex = selectedIndex,
                onTabSelected = { selectedIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
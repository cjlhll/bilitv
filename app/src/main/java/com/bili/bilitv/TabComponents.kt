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
import androidx.compose.ui.unit.dp

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
@Composable
fun <T> CommonTabRow(
    tabs: List<TabItem>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        items(tabs) { tab ->
            CommonTabButton(
                text = tab.title,
                selected = selectedTab.toString() == tab.id.toString(),
                onClick = {
                    @Suppress("UNCHECKED_CAST")
                    onTabSelected(tab.id as T)
                }
            )
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
@Composable
fun <T : Enum<T>> CommonTabRowWithEnum(
    tabs: Array<T>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        items(tabs.toList()) { tab ->
            CommonTabButton(
                text = tab.getTitle(),
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

/**
 * 扩展函数，为枚举类型提供自定义标题
 */
fun <T : Enum<T>> T.getTitle(): String {
    return when (this) {
        is TabType -> this.title
        else -> this.name
    }
}
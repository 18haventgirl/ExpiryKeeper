package com.expirykeeper.feature.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.CategoryPreset
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ItemSort
import com.expirykeeper.core.domain.DueStatus
import com.expirykeeper.core.domain.ReminderEngine
import com.expirykeeper.core.ui.designsystem.BigHeader
import com.expirykeeper.core.ui.designsystem.EmptyState
import com.expirykeeper.core.ui.designsystem.ItemCard
import com.expirykeeper.core.ui.designsystem.SectionHeader
import com.expirykeeper.core.ui.designsystem.StatusPill
import com.expirykeeper.core.ui.designsystem.StatusTone
import com.expirykeeper.core.ui.designsystem.Tone
import com.expirykeeper.ui.ItemsViewModel
import java.time.LocalDate

/** 清单 v2：搜索 + 4 路排序 + 品类分组（非吸顶头）；删除入口已移至详情屏（Task 11） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: ItemsViewModel, onDetail: (String) -> Unit) {
    val visible by vm.visibleItems.collectAsStateWithLifecycle()
    val query by vm.filterQuery.collectAsStateWithLifecycle()
    val sort by vm.sortOrder.collectAsStateWithLifecycle()
    var searchActive by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        BigHeader(
            title = "清单",
            subtitle = "${visible.size} 件",
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        ItemSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    vm.sortOrder.value = option
                                    menuOpen = false
                                },
                                trailingIcon = if (option == sort) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            },
        )
        SearchBar(
            query = query,
            onQueryChange = { vm.filterQuery.value = it },
            onSearch = { searchActive = false },
            active = searchActive,
            onActiveChange = { searchActive = it },
            placeholder = { Text("搜索名称 / 备注 / 位置") },
            modifier = Modifier.fillMaxWidth(),
        ) {}
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (visible.isEmpty()) {
                item {
                    if (query.isNotBlank()) {
                        EmptyState("🔍", "没有找到匹配的物品", "换个关键词试试")
                    } else {
                        EmptyState("📦", "还没有物品", "点右下角 ➕ 添加第一件")
                    }
                }
            } else if (sort == ItemSort.CATEGORY) {
                groupedCategories(visible).forEach { (rawId, cat, list) ->
                    item(key = "group-$rawId") { SectionHeader("${cat.emoji} ${cat.name}", list.size) }
                    items(list, key = { it.id }) { item ->
                        ListRow(item = item, onDetail = onDetail, today = today)
                    }
                }
            } else {
                items(visible, key = { it.id }) { item ->
                    ListRow(item = item, onDetail = onDetail, today = today)
                }
            }
        }
    }
}

/** 品类分组：预设顺序在前，未知 categoryId 兜底到末尾（不吞数据）；rawId 作分组键避免兜底预设撞 key */
private fun groupedCategories(
    visible: List<Item>,
): List<Triple<String, CategoryPreset, List<Item>>> {
    val grouped = visible.groupBy { it.categoryId }
    val known = Categories.all
        .filter { grouped.containsKey(it.id) }
        .map { Triple(it.id, it, grouped.getValue(it.id)) }
    val unknown = grouped.filterKeys { key -> Categories.byId(key) == null }
        .map { (key, list) -> Triple(key, Categories.default(key), list) }
    return known + unknown
}

/** 单行卡：状态胶囊（有提醒）或数量文本（无提醒）；点击/长按 → 详情浮层（Task 11） */
@Composable
private fun ListRow(item: Item, onDetail: (String) -> Unit, today: LocalDate) {
    val cat = Categories.default(item.categoryId)
    val reminder = ReminderEngine.computeOne(item, today)
    ItemCard(
        item = item,
        icon = ReminderEngine.displayIcon(item, cat.emoji),
        tone = if (reminder != null) {
            StatusTone(reminder.status)
        } else {
            Tone(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        },
        onClick = { onDetail(item.id) },
        onLongClick = { onDetail(item.id) },
    ) {
        if (reminder != null) {
            StatusPill(reminder.status, reminder.status.label)
        } else {
            Text(
                quantityText(item),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun quantityText(item: Item): String {
    val q = item.quantity ?: return "—"
    val num = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
    return "$num ${item.unit ?: ""}".trim()
}

private val ItemSort.label: String
    get() = when (this) {
        ItemSort.EXPIRE_ASC -> "按到期"
        ItemSort.NAME -> "按名称"
        ItemSort.CREATED_DESC -> "按添加时间"
        ItemSort.CATEGORY -> "按品类"
    }

private val DueStatus.label: String
    get() = when (this) {
        DueStatus.OVERDUE -> "逾期"
        DueStatus.DUE_TODAY -> "今天到期"
        DueStatus.DUE_SOON -> "即将到期"
        DueStatus.LOW_STOCK -> "库存低"
        DueStatus.RENEWAL_SOON -> "即将续费"
        DueStatus.RENEWAL_TODAY -> "今天续费"
    }

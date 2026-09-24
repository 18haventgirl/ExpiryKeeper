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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.CategoryPreset
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ItemSort
import com.expirykeeper.core.domain.daysCaption
import com.expirykeeper.core.domain.labelZh
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
    val total by vm.items.collectAsStateWithLifecycle()
    val query by vm.filterQuery.collectAsStateWithLifecycle()
    val sort by vm.sortOrder.collectAsStateWithLifecycle()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val today = LocalDate.now()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BigHeader(
            title = "清单",
            // 搜索时保留总数语境，否则「0 件」会让人以为东西没了（修 B12）
            subtitle = if (query.isBlank()) "${visible.size} 件" else "${visible.size} / ${total.size} 件",
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
                                    vm.setSortOrder(option)
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
        // 不展开的搜索输入框：下方列表本身实时过滤，展开式 SearchBar 的结果槽位是空的，
        // 会把已过滤出的结果盖住（SearchBar 收起态还会把状态栏 inset 再垫一遍）
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = { vm.setFilterQuery(it) },
            onSearch = { focusManager.clearFocus() },
            expanded = false,
            onExpandedChange = {},
            placeholder = { Text("搜索名称 / 备注 / 位置") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { vm.setFilterQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空搜索")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (visible.isEmpty() && !loading) {
            // 空态占满剩余空间居中，而不是顶在搜索框下面留一屏空白（修 B12）
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (query.isNotBlank()) {
                    EmptyState("🔍", "没有找到匹配的物品", "换个关键词，或清空搜索框")
                } else {
                    EmptyState("📦", "还没有物品", "点右下角 ➕ 添加第一件")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sort == ItemSort.CATEGORY) {
                    groupedCategories(visible).forEach { (rawId, cat, list) ->
                        item(key = "group-$rawId") {
                            SectionHeader(
                                "${cat.emoji} ${cat.name}",
                                count = list.size,
                                modifier = Modifier.animateItem().padding(top = 16.dp),
                            )
                        }
                        items(list, key = { it.id }) { item ->
                            ListRow(item = item, onDetail = onDetail, today = today, modifier = Modifier.animateItem())
                        }
                    }
                } else {
                    items(visible, key = { it.id }) { item ->
                        ListRow(item = item, onDetail = onDetail, today = today, modifier = Modifier.animateItem())
                    }
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

/** 单行卡：尾部只说「到期」这一件事，数量进副标题（修 A5：不再混用状态胶囊与光秃「—」） */
@Composable
private fun ListRow(
    item: Item,
    onDetail: (String) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val cat = Categories.default(item.categoryId)
    val reminder = ReminderEngine.computeOne(item, today)
    val expireDay = ReminderEngine.dueDayOf(item)
    ItemCard(
        item = item,
        icon = ReminderEngine.displayIcon(item, cat.emoji),
        tone = if (reminder != null) {
            StatusTone(reminder.status)
        } else {
            Tone(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        },
        // 字段之间用 " / "，把「·」留给品类名的层级（修 B11）
        detail = listOfNotNull(
            item.location ?: item.note,
            quantityLabel(item),
        ).joinToString(" / ").takeIf { it.isNotEmpty() },
        modifier = modifier,
        onClick = { onDetail(item.id) },
    ) {
        when {
            reminder != null -> StatusPill(reminder.status, reminder.status.labelZh)
            expireDay != null -> Text(
                daysCaption(expireDay - today.toEpochDay()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 数量 + 单位；无库存语义返回 null，交由副标题合并展示 */
private fun quantityLabel(item: Item): String? {
    val q = item.quantity ?: return null
    val num = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
    return "$num ${item.unit ?: ""}".trim().takeIf { it.isNotEmpty() }
}

private val ItemSort.label: String
    get() = when (this) {
        ItemSort.EXPIRE_ASC -> "按到期"
        ItemSort.NAME -> "按名称"
        ItemSort.CREATED_DESC -> "按添加时间"
        ItemSort.CATEGORY -> "按品类"
    }

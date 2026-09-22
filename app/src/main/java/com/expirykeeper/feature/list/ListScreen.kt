package com.expirykeeper.feature.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.ReminderKind
import com.expirykeeper.feature.today.ItemRow
import com.expirykeeper.ui.ItemsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ListScreen(vm: ItemsViewModel, onEdit: (String) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val grouped = items.groupBy { it.categoryId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Categories.all.forEach { cat ->
            val list = grouped[cat.id].orEmpty()
            if (list.isNotEmpty()) {
                item {
                    Text("${cat.emoji} ${cat.name}（${list.size}）",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp))
                }
                items(list, key = { it.id }) { item ->
                    val due = when (item.reminderKind) {
                        ReminderKind.EXPIRY -> item.expireAtEpochDay
                        ReminderKind.RECURRING -> item.nextDueAtEpochDay
                        ReminderKind.CONSUMABLE -> null
                    }?.let { LocalDate.ofEpochDay(it) }
                    Row(Modifier.fillMaxWidth().clickable { onEdit(item.id) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            ItemRow(
                                emoji = "", name = item.name,
                                trailing = due?.format(DateTimeFormatter.ofPattern("MM-dd")) ?: "-",
                                danger = due != null && !due.isAfter(LocalDate.now()),
                                subtitle = listOfNotNull(
                                    item.location,
                                    item.quantity?.let { "剩 ${it.toInt()} ${item.unit ?: ""}" },
                                ).joinToString(" · ").ifBlank { null },
                            )
                        }
                        TextButton(onClick = { vm.delete(item.id) }) { Text("删除") }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item { Text("还没有物品，点右下角 ➕ 添加第一件", Modifier.padding(24.dp)) }
        }
    }
}

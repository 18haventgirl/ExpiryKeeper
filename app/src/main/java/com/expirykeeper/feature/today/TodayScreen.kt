package com.expirykeeper.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.domain.DueStatus
import com.expirykeeper.core.domain.Reminder
import com.expirykeeper.core.domain.ReminderEngine
import com.expirykeeper.core.ui.designsystem.BigHeader
import com.expirykeeper.core.ui.designsystem.DueRing
import com.expirykeeper.core.ui.designsystem.EmptyState
import com.expirykeeper.core.ui.designsystem.ItemCard
import com.expirykeeper.core.ui.designsystem.SectionHeader
import com.expirykeeper.core.ui.designsystem.StatusTone
import com.expirykeeper.ui.ItemsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    vm: ItemsViewModel,
    onDetail: (String) -> Unit,
    onSettings: () -> Unit = {},
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val upcoming14 by vm.upcoming14.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    // 分组（controller ruling 8）：紧急=OVERDUE+DUE_TODAY，即将到期=DUE_SOON+RENEWAL_SOON，需要关注=LOW_STOCK+RENEWAL_TODAY
    val urgent = reminders.filter { it.status == DueStatus.OVERDUE || it.status == DueStatus.DUE_TODAY }
    val soon = reminders.filter { it.status == DueStatus.DUE_SOON || it.status == DueStatus.RENEWAL_SOON }
    val attention = reminders.filter { it.status == DueStatus.LOW_STOCK || it.status == DueStatus.RENEWAL_TODAY }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            BigHeader(
                title = "今日",
                subtitle = today.format(DateTimeFormatter.ofPattern("M月d日 · EEE", Locale.CHINA)),
                modifier = Modifier.padding(top = 8.dp),
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        }
        item {
            HeroCard(pending = reminders.size, upcoming = upcoming14.size, total = items.size)
        }
        if (reminders.isEmpty()) {
            item { EmptyState("🌿", "今天没有要处理的事", "去清单看看，或添加新物品") }
        }
        if (urgent.isNotEmpty()) {
            item { SectionHeader("紧急", urgent.size) }
            items(urgent, key = { it.notificationId }) { r ->
                ReminderCard(r = r, vm = vm, onDetail = onDetail, showActions = true, modifier = Modifier.animateItem())
            }
        }
        if (soon.isNotEmpty()) {
            item { SectionHeader("即将到期", soon.size) }
            items(soon, key = { it.notificationId }) { r ->
                ReminderCard(r = r, vm = vm, onDetail = onDetail, showActions = false, modifier = Modifier.animateItem())
            }
        }
        if (attention.isNotEmpty()) {
            item { SectionHeader("需要关注", attention.size) }
            items(attention, key = { it.notificationId }) { r ->
                ReminderCard(r = r, vm = vm, onDetail = onDetail, showActions = false, modifier = Modifier.animateItem())
            }
        }
        item { Spacer(Modifier.width(1.dp).padding(bottom = 12.dp)) }
    }
}

/** Hero 卡：三列 displaySmall 统计 + 空日安好事案 */
@Composable
private fun HeroCard(pending: Int, upcoming: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            HeroStat(pending.toString(), "待处理")
            HeroStat(upcoming.toString(), "两周内")
            HeroStat(total.toString(), "全部")
        }
        if (pending == 0) {
            Text(
                "今天一切安好 ✨",
                Modifier.padding(start = 24.dp, bottom = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** reminder 卡：ItemCard + DueRing（LOW_STOCK 无天数传 null）；紧急组下方 AnimatedVisibility 动作行 */
@Composable
private fun ReminderCard(
    r: Reminder,
    vm: ItemsViewModel,
    onDetail: (String) -> Unit,
    showActions: Boolean,
    modifier: Modifier = Modifier,
) {
    val item = r.item
    val cat = Categories.default(item.categoryId)
    Column(modifier.fillMaxWidth()) {
        ItemCard(
            item = item,
            icon = ReminderEngine.displayIcon(item, cat.emoji),
            tone = StatusTone(r.status),
            onClick = { onDetail(item.id) },
            onLongClick = { onDetail(item.id) },
        ) {
            DueRing(daysLeft = if (r.status == DueStatus.LOW_STOCK) null else r.daysLeft)
        }
        AnimatedVisibility(
            visible = showActions,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = { vm.rollForward(item.id) }, label = { Text("🍽 续期") })
                AssistChip(onClick = { vm.snooze3(r) }, label = { Text("😴 稍后3天") })
                AssistChip(onClick = { vm.markHandled(r) }, label = { Text("✅ 今天不再提醒") })
            }
        }
    }
}

package com.expirykeeper.feature.today

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.domain.DueStatus
import com.expirykeeper.core.domain.Reminder
import com.expirykeeper.core.domain.ReminderEngine
import com.expirykeeper.ui.ItemsViewModel
import java.time.LocalDate

@Composable
fun TodayScreen(vm: ItemsViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val reminders = ReminderEngine.computeForDate(items, today)
    val urgent = reminders.filter { it.status == DueStatus.OVERDUE || it.status == DueStatus.DUE_TODAY ||
        it.status == DueStatus.LOW_STOCK || it.status == DueStatus.RENEWAL_TODAY }
    val soon = reminders.filter { it.status == DueStatus.DUE_SOON || it.status == DueStatus.RENEWAL_SOON }
    val upcoming = ReminderEngine.upcoming(items, today, withinDays = 14)
        .filter { pair -> pair.second > 0 }
        .take(15)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.width(1.dp).padding(top = 4.dp)) }
        if (urgent.isEmpty() && soon.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text("今天没有要处理的事 ✅", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (urgent.isNotEmpty()) {
            item { SectionLabel("需要处理") }
            items(urgent, key = { it.notificationId }) { ReminderRow(it) }
        }
        if (soon.isNotEmpty()) {
            item { SectionLabel("即将到期") }
            items(soon, key = { it.notificationId }) { ReminderRow(it) }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionLabel("未来两周") }
            items(upcoming, key = { it.first.id }) { (item, daysLeft) ->
                val cat = Categories.default(item.categoryId)
                ItemRow(emoji = cat.emoji, name = item.name,
                    trailing = "$daysLeft 天后到期",
                    danger = daysLeft <= 3)
            }
        }
        item { Spacer(Modifier.width(1.dp).padding(bottom = 12.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ReminderRow(reminder: Reminder) {
    val item = reminder.item
    val cat = Categories.default(item.categoryId)
    val text = when (reminder.status) {
        DueStatus.DUE_SOON -> "还有 ${reminder.daysLeft} 天到期"
        DueStatus.DUE_TODAY -> "今天到期"
        DueStatus.OVERDUE -> "已过期 ${reminder.overdueDays} 天"
        DueStatus.LOW_STOCK -> "库存不足（剩 ${item.quantity?.toInt()} ${item.unit ?: ""}）"
        DueStatus.RENEWAL_SOON -> "还有 ${reminder.daysLeft} 天扣费"
        DueStatus.RENEWAL_TODAY -> "今天扣费"
    }
    ItemRow(emoji = cat.emoji, name = item.name, trailing = text,
        danger = reminder.status == DueStatus.OVERDUE || reminder.status == DueStatus.DUE_TODAY)
}

@Composable
fun ItemRow(emoji: String, name: String, trailing: String, danger: Boolean, subtitle: String? = null) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = if (danger) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant) {
                Text(trailing, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

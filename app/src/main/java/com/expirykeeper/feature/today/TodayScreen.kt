package com.expirykeeper.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.domain.DueStatus
import com.expirykeeper.core.domain.ReminderEngine
import com.expirykeeper.core.domain.RingSpec
import com.expirykeeper.core.domain.ringSpec
import com.expirykeeper.core.ui.designsystem.BigHeader
import com.expirykeeper.core.ui.designsystem.EkCard
import com.expirykeeper.core.ui.designsystem.DueRing
import com.expirykeeper.core.ui.designsystem.EmptyState
import com.expirykeeper.core.ui.designsystem.ItemCard
import com.expirykeeper.core.ui.designsystem.QuickActions
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
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    // 分组（修 A6）：紧急/需要关注来自引擎提醒；「即将到期」按 1..14 天区间取，与 hero 同一口径。
    // 引擎的 DUE_SOON 只在命中偏移日时触发（通知不该天天发），所以它不能充当列表数据源。
    val urgent = reminders.filter { it.status == DueStatus.OVERDUE || it.status == DueStatus.DUE_TODAY }
    val attention = reminders.filter { it.status == DueStatus.LOW_STOCK || it.status == DueStatus.RENEWAL_TODAY }
    val soon = ReminderEngine.soonSection(upcoming14, withinDays = 14L)
    val rowCount = urgent.size + soon.size + attention.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        // 底部留出 FAB 槽，否则最后一张卡的到期环会被 FAB 压住（修 B8，实测弧采样 0/8 坐实）
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            BigHeader(
                title = "今日",
                subtitle = today.format(DateTimeFormatter.ofPattern("M月d日 · EEE", Locale.CHINA)),
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        }
        item {
            // 三个数字各自等于本屏可见行数：待处理=全部行，两周内=即将到期行，全部=清单项数
            HeroCard(pending = rowCount, upcoming = soon.size, total = items.size)
        }
        if (rowCount == 0 && !loading) {
            item { EmptyState("🌿", "今天没有要处理的事", "去清单看看，或添加新物品") }
        }
        if (urgent.isNotEmpty()) {
            item { SectionHeader("紧急", count = urgent.size, modifier = Modifier.padding(top = 16.dp)) }
            items(urgent, key = { it.notificationId }) { r ->
                ReminderCard(
                    item = r.item,
                    status = r.status,
                    spec = ringSpec(r.status, r.daysLeft, r.overdueDays, r.item.reminderOffsetsDays),
                    onDetail = onDetail,
                    modifier = Modifier.animateItem(),
                    showActions = true,
                    actions = {
                        QuickActions(
                            onRollForward = { vm.rollForward(r.item.id) },
                            onSnooze = { vm.snooze3(r) },
                            onHandle = { vm.markHandled(r) },
                        )
                    },
                )
            }
        }
        if (soon.isNotEmpty()) {
            item { SectionHeader("即将到期", count = soon.size, modifier = Modifier.padding(top = 16.dp)) }
            items(soon, key = { "soon-${it.first.id}" }) { (soonItem, days) ->
                ReminderCard(
                    item = soonItem,
                    status = DueStatus.DUE_SOON,
                    spec = ringSpec(DueStatus.DUE_SOON, days, 0, soonItem.reminderOffsetsDays),
                    onDetail = onDetail,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (attention.isNotEmpty()) {
            item { SectionHeader("需要关注", count = attention.size, modifier = Modifier.padding(top = 16.dp)) }
            items(attention, key = { it.notificationId }) { r ->
                ReminderCard(
                    item = r.item,
                    status = r.status,
                    spec = ringSpec(r.status, r.daysLeft, r.overdueDays, r.item.reminderOffsetsDays),
                    onDetail = onDetail,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * Hero 卡：三列统计走「一处下重注」的编辑式层级 —— 待处理是主数字（headlineLarge），
 * 两周内/全部是次级语境（headlineSmall + 次要色），避免三个 40sp 同权重互相抵消（修 B6）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(pending: Int, upcoming: Int, total: Int) {
    EkCard(title = null, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
        // FlowRow 而非 Row：大字号（长辈模式 200%）下三个统计会换行而不是被裁（修 B14）
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroStat(pending.toString(), "待处理", prominent = true)
            HeroStat(upcoming.toString(), "两周内", prominent = false)
            HeroStat(total.toString(), "全部", prominent = false)
        }
        if (pending == 0) {
            Text(
                "今天一切安好 ✨",
                Modifier.padding(start = 16.dp, bottom = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, prominent: Boolean) {
    Column {
        Text(
            value,
            style = if (prominent) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
            color = if (prominent) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 今日行卡：ItemCard + DueRing（环的文本与弧由 ringSpec 决定）；紧急组经 actions 槽挂快捷操作 */
@Composable
private fun ReminderCard(
    item: Item,
    status: DueStatus,
    spec: RingSpec,
    onDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    showActions: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    val tone = StatusTone(status)
    Column(modifier.fillMaxWidth()) {
        ItemCard(
            item = item,
            icon = ReminderEngine.displayIcon(item, Categories.default(item.categoryId).emoji),
            tone = tone,
            onClick = { onDetail(item.id) },
        ) {
            DueRing(spec, tone)
        }
        AnimatedVisibility(
            visible = showActions,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { actions() }
        }
    }
}

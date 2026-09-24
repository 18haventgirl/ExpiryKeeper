package com.expirykeeper.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ItemEvent
import com.expirykeeper.core.domain.dateZh
import com.expirykeeper.core.domain.daysCaption
import com.expirykeeper.core.domain.labelZh
import com.expirykeeper.core.domain.ReminderEngine
import com.expirykeeper.core.ui.designsystem.QuickActions
import com.expirykeeper.core.ui.designsystem.SectionHeader
import com.expirykeeper.core.ui.designsystem.StatusPill
import com.expirykeeper.ui.ItemsViewModel
import java.time.LocalDate

/**
 * 详情浮层（Task 11）：注册在 `detail/{id}` 路由内、以 ModalBottomSheet 覆盖来路屏。
 * 数据源 repo.observeById 实时流；快速操作复用 VM；删除走 deleteWithUndo（Snackbar 可撤销）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    vm: ItemsViewModel,
    itemId: String,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        var item by remember(itemId) { mutableStateOf<Item?>(null) }
        var loaded by remember(itemId) { mutableStateOf(false) }
        var events by remember(itemId) { mutableStateOf<List<ItemEvent>>(emptyList()) }
        LaunchedEffect(itemId) {
            vm.observeItem(itemId).collect {
                item = it
                loaded = true
            }
        }
        LaunchedEffect(itemId) { events = vm.recentEventsFor(itemId) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = item
            when {
                !loaded -> Text("加载中…", style = MaterialTheme.typography.bodyMedium)
                current == null || current.deletedAt != null -> {
                    // 控制裁决 7：物品已被他处删除 → 空态 + 关闭
                    Text("该物品已不在清单中", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onDismiss) { Text("关闭") }
                }
                else -> DetailContent(current, events, vm, onEdit, onDismiss)
            }
        }
    }
}

@Composable
private fun DetailContent(
    item: Item,
    events: List<ItemEvent>,
    vm: ItemsViewModel,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cat = Categories.default(item.categoryId)
    val today = LocalDate.now()
    val reminder = ReminderEngine.computeOne(item, today)

    // 头部：emoji 44sp + 名称 + 品类·位置
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ReminderEngine.displayIcon(item, cat.emoji), fontSize = 44.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleLarge)
            val subtitle = listOfNotNull(cat.name, item.location).joinToString(" / ")
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // 当前状态：有提醒 → StatusPill；引擎静默/无规则 → "节奏正常"
    if (reminder != null) {
        StatusPill(reminder.status, reminder.status.labelZh)
    } else {
        NeutralPill("节奏正常")
    }

    // 键值行
    val expire = ReminderEngine.effectiveExpireDay(item)
    if (expire != null) {
        KvRow("到期日", dateZh(LocalDate.ofEpochDay(expire), today))
        KvRow("剩余", daysCaption(expire - today.toEpochDay()))
    }
    if (item.openedAtEpochDay != null || item.shelfLifeDays != null) {
        val opened = item.openedAtEpochDay?.let { dateZh(LocalDate.ofEpochDay(it), today) } ?: "—"
        val life = item.shelfLifeDays?.let { "保质期 $it 天" } ?: "未设保质期"
        KvRow("开封 / 保质期", "$opened / $life")
    }
    item.nextDueAtEpochDay?.let { KvRow("下次续费", dateZh(LocalDate.ofEpochDay(it), today)) }
    item.recurrenceDays?.let { KvRow("周期", "每 $it 天") }
    item.quantity?.let { q ->
        val num = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
        KvRow("数量", "$num ${item.unit ?: ""}".trim())
    }
    item.lowStockThreshold?.let { KvRow("低库存线", if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()) }
    item.barcode?.takeIf { it.isNotBlank() }?.let { KvRow("条码", it) }
    item.note?.takeIf { it.isNotBlank() }?.let { KvRow("备注", it) }

    // 快速操作行：与今日屏共用一份组件（snooze/handle 依赖 Reminder 快照，无提醒时只留续期）
    QuickActions(
        onRollForward = { vm.rollForward(item.id) },
        onSnooze = reminder?.let { r -> { vm.snooze3(r) } },
        onHandle = reminder?.let { r -> { vm.markHandled(r) } },
    )

    // 最近记录：30 天事件流水，kind → 中文
    SectionHeader("最近记录")
    if (events.isEmpty()) {
        Text("暂无记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        events.take(10).forEach { e ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    dateZh(LocalDate.ofEpochDay(e.epochDay), today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 下限保住对齐，上限容得下跨年时的「2027年1月5日」长式
                    modifier = Modifier.widthIn(min = 72.dp, max = 132.dp).padding(end = 12.dp),
                    maxLines = 1,
                )
                Text(eventKindLabel(e.kind), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // 底部：编辑 + 删除（唯一删除路径，撤销走 Snackbar）
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { onEdit(item.id) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("编辑")
        }
        FilledTonalButton(
            onClick = { vm.deleteWithUndo(item.id); onDismiss() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { Text("删除") }
    }
}

@Composable
private fun KvRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** 无提醒时的中性状态胶囊（与 StatusPill 同形状，surfaceVariant 底） */
@Composable
private fun NeutralPill(label: String) {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

private fun eventKindLabel(kind: String): String = when (kind) {
    "add" -> "添加"
    "roll" -> "已续期"
    "snooze" -> "已延后"
    "handle" -> "已处理"
    "edit" -> "编辑"
    "delete" -> "已删除"
    else -> kind
}

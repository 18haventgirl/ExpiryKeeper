package com.expirykeeper.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.expirykeeper.core.ui.designsystem.KeyValueRow
import com.expirykeeper.core.ui.designsystem.NeutralTone
import com.expirykeeper.core.ui.designsystem.Pill
import com.expirykeeper.core.ui.designsystem.QuickActions
import com.expirykeeper.core.ui.designsystem.SectionHeader
import com.expirykeeper.core.ui.designsystem.StatusPill
import com.expirykeeper.ui.ItemsViewModel
import java.time.LocalDate

/**
 * 详情浮层（Task 11）：挂在 EkApp 根层之上（不是路由，避免来路屏被清空）。
 * 数据源 repo.observeById 实时流；快速操作复用 VM；删除走 deleteWithUndo（Snackbar 可撤销）。
 * 结构：可滚内容区（头部/键值/快速操作/最近记录）+ 钉在浮层下沿的页脚（编辑/删除）。
 * 浮层高度随内容自然伸缩（不写死百分比）；页脚在滚动区之外，所以内容再多也滑不走它。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    vm: ItemsViewModel,
    itemId: String,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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

        val current = item
        val live = current.takeIf { it?.deletedAt == null }

        // 浮层高度写死（用户裁决 B）：不管哪个物品、内容几行，弹出来都一样高，
        // 省掉"内容少的物品浮层矮一截"的参差感。页脚**浮在内容之上**而不是占一条，
        // 所以内容会从它底下穿过 —— 页脚因此必须自带衬底，见 SheetFooter。
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(SheetHeightFraction),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        // 底部留出页脚的高度，滑到底时最后一条记录不会被压在按钮下面
                        .padding(top = 4.dp, bottom = FooterClearance),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when {
                        !loaded -> Text("加载中…", style = MaterialTheme.typography.bodyMedium)
                        current == null || current.deletedAt != null -> {
                            // 控制裁决 7：物品已被他处删除 → 空态 + 关闭
                            Text("该物品已不在清单中", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = onDismiss) { Text("关闭") }
                        }
                        else -> DetailContent(current, events, vm)
                    }
                }
                if (live != null) {
                    SheetFooter(
                        live, vm, onEdit, onDismiss,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

private val SheetHeightFraction = 0.62f
private val FooterClearance = 96.dp
private val ScrimHeight = 20.dp

/**
 * 浮在内容之上的页脚：编辑 / 删除。
 *
 * 位置永远在浮层下沿，不随内容滚动。因为内容会从它底下划过，这里必须铺一层
 * 与浮层同色的衬底，并在其上接一段渐隐——否则长备注/记录文字会在按钮下方
 * 若隐若现（M3 的 bottom-bar 衬底同理）。衬底取 surfaceContainerLow，
 * 与 ModalBottomSheet 默认容器色一致，深浅两套主题都实测过像素对齐。
 */
@Composable
private fun SheetFooter(
    item: Item,
    vm: ItemsViewModel,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ScrimHeight)
                .background(Brush.verticalGradient(listOf(Color.Transparent, base))),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(base)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
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
}

@Composable
private fun DetailContent(item: Item, events: List<ItemEvent>, vm: ItemsViewModel) {
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
        Pill(NeutralTone(), "节奏正常")
    }

    // 键值行
    val expire = ReminderEngine.effectiveExpireDay(item)
    if (expire != null) {
        KeyValueRow("到期日", dateZh(LocalDate.ofEpochDay(expire), today))
        KeyValueRow("剩余", daysCaption(expire - today.toEpochDay()))
    }
    if (item.openedAtEpochDay != null || item.shelfLifeDays != null) {
        val opened = item.openedAtEpochDay?.let { dateZh(LocalDate.ofEpochDay(it), today) } ?: "—"
        val life = item.shelfLifeDays?.let { "保质期 $it 天" } ?: "未设保质期"
        KeyValueRow("开封 / 保质期", "$opened / $life")
    }
    item.nextDueAtEpochDay?.let { KeyValueRow("下次续费", dateZh(LocalDate.ofEpochDay(it), today)) }
    item.recurrenceDays?.let { KeyValueRow("周期", "每 $it 天") }
    item.quantity?.let { q ->
        val num = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
        KeyValueRow("数量", "$num ${item.unit ?: ""}".trim())
    }
    item.lowStockThreshold?.let { KeyValueRow("低库存线", if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()) }
    item.barcode?.takeIf { it.isNotBlank() }?.let { KeyValueRow("条码", it) }
    item.note?.takeIf { it.isNotBlank() }?.let { KeyValueRow("备注", it) }

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
}


/** 无提醒时的中性状态胶囊（与 StatusPill 同形状，surfaceVariant 底） */

private fun eventKindLabel(kind: String): String = when (kind) {
    "add" -> "添加"
    "roll" -> "已续期"
    "snooze" -> "已延后"
    "handle" -> "已处理"
    "edit" -> "编辑"
    "delete" -> "已删除"
    else -> kind
}

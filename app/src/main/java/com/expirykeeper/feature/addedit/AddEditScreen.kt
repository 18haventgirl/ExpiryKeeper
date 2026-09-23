package com.expirykeeper.feature.addedit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import com.expirykeeper.core.domain.ExpiryForm
import com.expirykeeper.core.domain.ExpiryFormMode
import com.expirykeeper.core.domain.RuleState
import com.expirykeeper.core.ui.designsystem.BigHeader
import com.expirykeeper.ui.ItemsViewModel
import java.time.LocalDate

/**
 * 添加/编辑 v2（Task 10）：四段式 Card（品类 / 名称与图标 / 到期规则 / 更多设置）。
 * EXPIRY 品类走 [到期日期 | 开封+保质期] 分段；开封模式只写 openedAt+shelfLife，
 * 到期日由 ItemRepository.save 派生（UI 不计算落库值）。校验失败禁用保存 + 行内红字。
 */
@Composable
fun AddEditScreen(vm: ItemsViewModel, itemId: String?, onDone: () -> Unit) {
    var editing by remember { mutableStateOf<Item?>(null) }
    var name by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(Categories.all.first().id) }
    var emoji by remember { mutableStateOf<String?>(null) }
    var showEmojiSheet by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var offsetsText by remember { mutableStateOf("3,0") }
    // EXPIRY：分段模式 + 两套字段
    var expiryMode by remember { mutableStateOf(ExpiryFormMode.DATE) }
    var expireDate by remember { mutableStateOf<LocalDate?>(null) }
    var openedDate by remember { mutableStateOf<LocalDate?>(null) }
    var shelfChoice by remember { mutableStateOf<Int?>(null) }
    var shelfCustom by remember { mutableStateOf(false) }
    var shelfText by remember { mutableStateOf("") }
    // CONSUMABLE
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf("") }
    // RECURRING
    var nextDueDate by remember { mutableStateOf<LocalDate?>(null) }
    var recurrenceChoice by remember { mutableStateOf<Int?>(null) }
    var recurrenceCustom by remember { mutableStateOf(false) }
    var recurrenceText by remember { mutableStateOf("") }
    var moreOpen by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(itemId == null) }
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        if (itemId != null) {
            vm.getById(itemId)?.let { item ->
                val cat = Categories.default(item.categoryId)
                editing = item
                name = item.name
                categoryId = item.categoryId
                emoji = item.emoji
                location = item.location ?: ""
                note = item.note ?: ""
                barcode = item.barcode ?: ""
                offsetsText = item.reminderOffsetsDays.joinToString(",")
                expiryMode = ExpiryForm.modeOf(item)
                expireDate = item.expireAtEpochDay?.let(LocalDate::ofEpochDay)
                openedDate = item.openedAtEpochDay?.let(LocalDate::ofEpochDay)
                item.shelfLifeDays?.let { days ->
                    if (cat.defaultShelfLifeChoicesDays.contains(days)) shelfChoice = days
                    else { shelfCustom = true; shelfText = days.toString() }
                }
                quantity = item.quantity?.let { formatQty(it) } ?: ""
                unit = item.unit ?: ""
                threshold = item.lowStockThreshold?.let { formatQty(it) } ?: ""
                nextDueDate = item.nextDueAtEpochDay?.let(LocalDate::ofEpochDay)
                item.recurrenceDays?.let { days ->
                    if (days in RecurrenceChoices) recurrenceChoice = days
                    else { recurrenceCustom = true; recurrenceText = days.toString() }
                }
                moreOpen = listOf(location, note, barcode).any { it.isNotBlank() } ||
                    offsetsText != cat.defaultOffsetsDays.joinToString(",")
            }
            loaded = true
        }
    }
    if (!loaded) {
        // 修 B4：编辑既有物品时不再整页空帧，回填前给一个居中的加载指示
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val cat = Categories.default(categoryId)
    val shelfLife = if (shelfCustom) ExpiryForm.parseDays(shelfText) else shelfChoice
    val recurrence = if (recurrenceCustom) ExpiryForm.parseDays(recurrenceText) else recurrenceChoice
    val shelfInvalid = shelfCustom && shelfText.isNotBlank() && shelfLife == null
    val qtyErr = positiveNumErr(quantity, "数量")
    val thresholdErr = positiveNumErr(threshold, "低库存线")

    val nameErr = if (name.isBlank()) "请填写物品名称" else null
    val ruleErr = ExpiryForm.ruleError(
        RuleState(
            kind = cat.reminderKind,
            mode = expiryMode,
            expireDate = expireDate,
            openedDate = openedDate,
            shelfLife = shelfLife,
            nextDueDate = nextDueDate,
            recurrence = recurrence,
            quantityError = qtyErr,
            thresholdError = thresholdErr,
        ),
    )
    val valid = nameErr == null && ruleErr == null && !shelfInvalid
    // 修 A3：错误文本只在用户按过一次保存后才出现，新建首帧不再满屏红
    val shownNameErr = nameErr.takeIf { revealed }
    val shownRuleErr = ruleErr.takeIf { revealed }

    fun pickCategory(picked: String) {
        categoryId = picked
        val preset = Categories.default(picked)
        offsetsText = preset.defaultOffsetsDays.joinToString(",")
        shelfChoice = null; shelfCustom = false; shelfText = ""
    }

    fun switchExpiryMode(mode: ExpiryFormMode) {
        expiryMode = mode
        // 开封模式默认今天：牛奶路径"分段→chip→保存"零键盘
        if (mode == ExpiryFormMode.OPENED && openedDate == null) openedDate = LocalDate.now()
    }

    fun buildItem(): Item {
        val base = editing ?: Item(id = java.util.UUID.randomUUID().toString(), name = "", categoryId = categoryId)
        val offsets = offsetsText.split(",", "，").mapNotNull { it.trim().toIntOrNull() }.sortedDescending()
        val item = base.copy(
            name = name.trim(),
            categoryId = categoryId,
            location = location.trim().ifBlank { null },
            note = note.trim().ifBlank { null },
            barcode = barcode.trim().ifBlank { null },
            emoji = emoji,
            reminderKind = cat.reminderKind,
            reminderOffsetsDays = offsets.ifEmpty { listOf(0) },
            quantity = quantity.toDoubleOrNull(),
            unit = unit.trim().ifBlank { null },
            lowStockThreshold = threshold.toDoubleOrNull(),
        )
        // I-3b：按目标 reminderKind 重建，显式清空其他 kind 的专属字段，
        // 使"编辑换类"落库形态与新建同 kind 一致（新建默认全 null），不携带原 kind 脏值。
        // quantity/unit/lowStockThreshold 恒由 CONSUMABLE 表单文本派生（非该类时为空→null），无需再清。
        return when (cat.reminderKind) {
            // 互斥清理走 ExpiryForm：换模式即清空另一侧字段，到期日派生留给 repo.save
            ReminderKind.EXPIRY -> {
                val expiry = when (expiryMode) {
                    ExpiryFormMode.DATE -> ExpiryForm.applyDateMode(item, expireDate?.toEpochDay())
                    ExpiryFormMode.OPENED -> ExpiryForm.applyOpenedMode(item, openedDate?.toEpochDay(), shelfLife)
                }
                expiry.copy(nextDueAtEpochDay = null, recurrenceDays = null)
            }
            ReminderKind.RECURRING -> item.copy(
                nextDueAtEpochDay = nextDueDate?.toEpochDay(),
                recurrenceDays = recurrence, // I-4：无静默回退；invalid/未选时按钮已被 ruleErr 禁用
                expireAtEpochDay = null, openedAtEpochDay = null, shelfLifeDays = null,
            )
            ReminderKind.CONSUMABLE -> item.copy(
                expireAtEpochDay = null, openedAtEpochDay = null, shelfLifeDays = null,
                nextDueAtEpochDay = null, recurrenceDays = null,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BigHeader(
            title = if (editing == null) "添加物品" else "编辑物品",
            subtitle = cat.shelfLifeHint.ifBlank { cat.name },
            onBack = onDone,
            modifier = Modifier.padding(top = 8.dp),
        )

        FormCard("品类") { CategoryStrip(selected = categoryId, onPick = { pickCategory(it) }) }

        FormCard("名称与图标") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("物品名称 *") }, isError = shownNameErr != null, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                EmojiIconButton(display = emoji ?: cat.emoji) { showEmojiSheet = true }
            }
            ErrorLine(shownNameErr)
            Text("点右侧图标可自选 emoji，默认跟随品类", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        FormCard("到期规则") {
            when (cat.reminderKind) {
                ReminderKind.EXPIRY -> ExpiryRuleSection(
                    shelfChoices = cat.defaultShelfLifeChoicesDays
                        .filter { it in ExpiryForm.ShelfLifeRange }.distinct().take(6),
                    expiryMode = expiryMode,
                    onModeChange = { switchExpiryMode(it) },
                    expireDate = expireDate,
                    onExpireDate = { expireDate = it },
                    openedDate = openedDate,
                    onOpenedDate = { openedDate = it },
                    shelfChoice = shelfChoice,
                    onShelfChip = { shelfChoice = it; shelfCustom = false },
                    shelfCustom = shelfCustom,
                    onShelfCustom = { shelfCustom = true },
                    shelfText = shelfText,
                    onShelfText = { shelfText = it },
                    shelfLife = shelfLife,
                    shelfInvalid = shelfInvalid,
                )
                ReminderKind.CONSUMABLE -> ConsumableRuleSection(
                    quantity = quantity, onQuantity = { quantity = it },
                    threshold = threshold, onThreshold = { threshold = it },
                    unit = unit, onUnit = { unit = it },
                )
                ReminderKind.RECURRING -> RecurringRuleSection(
                    choices = RecurrenceChoices,
                    nextDueDate = nextDueDate,
                    onNextDueDate = { nextDueDate = it },
                    recurrenceChoice = recurrenceChoice,
                    onChoiceChip = { recurrenceChoice = it; recurrenceCustom = false },
                    recurrenceCustom = recurrenceCustom,
                    onCustomChip = { recurrenceCustom = true },
                    recurrenceText = recurrenceText,
                    onTextChange = { recurrenceText = it },
                )
            }
            ErrorLine(shownRuleErr)
        }

        FormCard("更多设置") {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { moreOpen = !moreOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("备注 / 位置 / 条码 / 提醒提前量",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                Icon(if (moreOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (moreOpen) "收起" else "展开")
            }
            AnimatedVisibility(visible = moreOpen, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = note, onValueChange = { note = it },
                        label = { Text("备注（选填）") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = location, onValueChange = { location = it },
                        label = { Text("存放位置（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it },
                        label = { Text("条码（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = offsetsText, onValueChange = { offsetsText = it },
                        label = { Text("提前提醒天数（逗号分隔，0=当天）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 保存不置灰：无效时点一次即揭示缺什么（修 A3，同时消掉不可读的禁用态文字）
            Button(onClick = {
                if (valid) {
                    vm.save(buildItem())
                    onDone()
                } else {
                    revealed = true
                }
            }, modifier = Modifier.weight(1f)) {
                Text(if (editing == null) "保存" else "更新")
            }
            // 删除入口唯一在详情浮层（Task 11：deleteWithUndo 可撤销），此处不再提供
            TextButton(onClick = onDone) { Text("取消") }
        }
        Spacer(Modifier.width(1.dp).padding(bottom = 24.dp))
    }

    if (showEmojiSheet) {
        EmojiPickerSheet(current = emoji, category = categoryId,
            onPick = { emoji = it; showEmojiSheet = false },
            onDismiss = { showEmojiSheet = false })
    }
}

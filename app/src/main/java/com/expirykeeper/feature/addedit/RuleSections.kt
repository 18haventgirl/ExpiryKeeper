package com.expirykeeper.feature.addedit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expirykeeper.core.domain.ExpiryFormMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 到期规则各 reminderKind 的分支 UI（AddEditScreen 段3，拆文件遵循 CODESTYLE §300 行）。
 * 全部单向数据流：值 + 回调由屏组合提升而来，本文件不持有任何表单状态。
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ExpiryRuleSection(
    shelfChoices: List<Int>,
    expiryMode: ExpiryFormMode,
    onModeChange: (ExpiryFormMode) -> Unit,
    expireDate: LocalDate?,
    onExpireDate: (LocalDate?) -> Unit,
    openedDate: LocalDate?,
    onOpenedDate: (LocalDate?) -> Unit,
    shelfChoice: Int?,
    onShelfChip: (Int) -> Unit,
    shelfCustom: Boolean,
    onShelfCustom: () -> Unit,
    shelfText: String,
    onShelfText: (String) -> Unit,
    shelfLife: Int?,
    shelfInvalid: Boolean,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = expiryMode == ExpiryFormMode.DATE,
            onClick = { onModeChange(ExpiryFormMode.DATE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("到期日期") }
        SegmentedButton(
            selected = expiryMode == ExpiryFormMode.OPENED,
            onClick = { onModeChange(ExpiryFormMode.OPENED) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text("开封+保质期") }
    }
    if (expiryMode == ExpiryFormMode.DATE) {
        DateField("到期日期 *", expireDate, onExpireDate)
    } else {
        DateField("开封日期 *", openedDate, onOpenedDate)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shelfChoices.forEach { days ->
                FilterChip(
                    selected = !shelfCustom && shelfChoice == days,
                    onClick = { onShelfChip(days) },
                    label = { Text("${days}天") },
                )
            }
            FilterChip(selected = shelfCustom, onClick = onShelfCustom, label = { Text("自定义") })
        }
        AnimatedVisibility(visible = shelfCustom) {
            OutlinedTextField(
                value = shelfText, onValueChange = onShelfText,
                label = { Text("保质期天数（1~3650）*") },
                isError = shelfInvalid || (shelfCustom && shelfText.isBlank()),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        // 预览仅供确认，落库到期日由 repo.save 派生，与此处展示无关
        val preview = openedDate?.plusDays((shelfLife ?: 0).toLong())
        AnimatedVisibility(visible = openedDate != null && shelfLife != null) {
            Text(
                "预计 ${preview?.format(DateTimeFormatter.ISO_LOCAL_DATE)} 到期",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.RecurringRuleSection(
    choices: List<Int>,
    nextDueDate: LocalDate?,
    onNextDueDate: (LocalDate?) -> Unit,
    recurrenceChoice: Int?,
    onChoiceChip: (Int) -> Unit,
    recurrenceCustom: Boolean,
    onCustomChip: () -> Unit,
    recurrenceText: String,
    onTextChange: (String) -> Unit,
) {
    DateField("下次扣费日期 *", nextDueDate, onNextDueDate)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { days ->
            FilterChip(
                selected = !recurrenceCustom && recurrenceChoice == days,
                onClick = { onChoiceChip(days) },
                label = { Text("每${days}天") },
            )
        }
        FilterChip(selected = recurrenceCustom, onClick = onCustomChip, label = { Text("自定义") })
    }
    AnimatedVisibility(visible = recurrenceCustom) {
        OutlinedTextField(
            value = recurrenceText, onValueChange = onTextChange,
            label = { Text("周期天数（1~3650）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ConsumableRuleSection(
    quantity: String,
    onQuantity: (String) -> Unit,
    threshold: String,
    onThreshold: (String) -> Unit,
    unit: String,
    onUnit: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = quantity, onValueChange = onQuantity,
            label = { Text("当前数量") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = threshold, onValueChange = onThreshold,
            label = { Text("低库存线") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = unit, onValueChange = onUnit,
            label = { Text("单位") }, singleLine = true, modifier = Modifier.weight(1f))
    }
}

internal val RecurrenceChoices = listOf(30, 90, 365)

/** 库存数字：填了就必须是 >0 的 double，空 = 选填 */
internal fun positiveNumErr(text: String, label: String): String? {
    if (text.isBlank()) return null
    val v = text.toDoubleOrNull()
    return if (v != null && v > 0.0) null else "${label}需为大于 0 的数字"
}

/** 回填展示：整数量去掉小数尾巴（10.0 → 10） */
internal fun formatQty(q: Double): String = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()

package com.expirykeeper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expirykeeper.data.Categories
import com.expirykeeper.data.CategoryPreset
import com.expirykeeper.data.Item
import com.expirykeeper.data.ReminderKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AddEditScreen(vm: ItemsViewModel, itemId: String?, onDone: () -> Unit) {
    var editing by remember { mutableStateOf<Item?>(null) }
    var name by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(Categories.all.first().id) }
    var location by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var expireDate by remember { mutableStateOf<LocalDate?>(null) }
    var offsetsText by remember { mutableStateOf("3,0") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf("") }
    var nextDueDate by remember { mutableStateOf<LocalDate?>(null) }
    var loaded by remember { mutableStateOf(itemId == null) }

    LaunchedEffect(itemId) {
        if (itemId != null) {
            vm.getById(itemId)?.let { item ->
                editing = item
                name = item.name
                categoryId = item.categoryId
                location = item.location ?: ""
                note = item.note ?: ""
                expireDate = item.expireAtEpochDay?.let(LocalDate::ofEpochDay)
                offsetsText = item.reminderOffsetsDays.joinToString(",")
                quantity = item.quantity?.toInt()?.toString() ?: ""
                unit = item.unit ?: ""
                threshold = item.lowStockThreshold?.toInt()?.toString() ?: ""
                nextDueDate = item.nextDueAtEpochDay?.let(LocalDate::ofEpochDay)
            }
            loaded = true
        }
    }
    if (!loaded) return

    val cat = Categories.default(categoryId)
    var nameError by remember { mutableStateOf(false) }

    fun buildItem(): Item {
        val base = editing ?: Item(id = java.util.UUID.randomUUID().toString(), name = "", categoryId = categoryId)
        val offsets = offsetsText.split(",", "，").mapNotNull { it.trim().toIntOrNull() }.sortedDescending()
        return base.copy(
            name = name.trim(),
            categoryId = categoryId,
            location = location.trim().ifBlank { null },
            note = note.trim().ifBlank { null },
            reminderKind = cat.reminderKind,
            reminderOffsetsDays = offsets.ifEmpty { listOf(0) },
            expireAtEpochDay = if (cat.reminderKind == ReminderKind.EXPIRY) expireDate?.toEpochDay() else base.expireAtEpochDay,
            quantity = quantity.toDoubleOrNull(),
            unit = unit.trim().ifBlank { null },
            lowStockThreshold = threshold.toDoubleOrNull(),
            nextDueAtEpochDay = if (cat.reminderKind == ReminderKind.RECURRING) nextDueDate?.toEpochDay() else base.nextDueAtEpochDay,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.width(1.dp))
        OutlinedTextField(value = name, onValueChange = { name = it; nameError = false },
            label = { Text("物品名称 *") }, isError = nameError, singleLine = true,
            modifier = Modifier.fillMaxWidth())

        Text("品类", style = MaterialTheme.typography.labelLarge)
        CategoryChips(selected = categoryId) { picked ->
            categoryId = picked
            val preset = Categories.default(picked)
            offsetsText = preset.defaultOffsetsDays.joinToString(",")
        }

        if (cat.shelfLifeHint.isNotBlank()) Text("💡 ${cat.shelfLifeHint}", style = MaterialTheme.typography.bodySmall)

        when (cat.reminderKind) {
            ReminderKind.EXPIRY -> DateField("到期日期 *", expireDate) { expireDate = it }
            ReminderKind.RECURRING -> DateField("下次扣费日期 *", nextDueDate) { nextDueDate = it }
            ReminderKind.CONSUMABLE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = quantity, onValueChange = { quantity = it },
                    label = { Text("当前数量") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = threshold, onValueChange = { threshold = it },
                    label = { Text("低库存线") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = unit, onValueChange = { unit = it },
                    label = { Text("单位") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }

        OutlinedTextField(value = offsetsText, onValueChange = { offsetsText = it },
            label = { Text("提前提醒天数（逗号分隔，0=当天）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())

        if (cat.reminderKind == ReminderKind.EXPIRY && cat.defaultShelfLifeChoicesDays.isNotEmpty()) {
            Text("或按保质期从今天的快填", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cat.defaultShelfLifeChoicesDays.take(6).forEach { days ->
                    FilterChip(selected = false, onClick = {
                        expireDate = LocalDate.now().plusDays(days.toLong())
                    }, label = { Text("${days}天") })
                }
            }
        }

        OutlinedTextField(value = location, onValueChange = { location = it },
            label = { Text("存放位置（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = note, onValueChange = { note = it },
            label = { Text("备注（选填）") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (name.isBlank()) { nameError = true; return@Button }
                val item = buildItem()
                val ok = when (Categories.default(item.categoryId).reminderKind) {
                    ReminderKind.EXPIRY -> item.expireAtEpochDay != null
                    ReminderKind.RECURRING -> item.nextDueAtEpochDay != null
                    ReminderKind.CONSUMABLE -> true
                }
                if (ok) { vm.save(item); onDone() } else { nameError = false }
            }, modifier = Modifier.weight(1f)) { Text(if (editing == null) "保存" else "更新") }
            if (editing != null) {
                OutlinedButton(onClick = { vm.delete(editing!!.id); onDone() }) { Text("删除") }
            }
            TextButton(onClick = onDone) { Text("取消") }
        }
        Spacer(Modifier.width(1.dp).padding(bottom = 24.dp))
    }
}

@Composable
private fun CategoryChips(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Categories.all.forEach { preset: CategoryPreset ->
            FilterChip(selected = selected == preset.id, onClick = { onPick(preset.id) },
                label = { Text("${preset.emoji}${preset.name}") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: LocalDate?, onValue: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text(value?.let { "$label：${it.format(DateTimeFormatter.ISO_DATE)}" } ?: "$label（点击选择）")
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onValue(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
                    }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

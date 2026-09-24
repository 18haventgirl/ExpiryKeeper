package com.expirykeeper.feature.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.CategoryPreset
import com.expirykeeper.core.domain.dateZh
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 行内校验错误：labelSmall + error 色（替代 Toast，随字段常驻提示） */
@Composable
fun ErrorLine(text: String?) {
    if (text != null) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** 品类横滚条：emoji + 名称双行胶囊，选中 secondaryContainer */
@Composable
fun CategoryStrip(selected: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Categories.all.forEach { preset: CategoryPreset ->
            PCategoryChip(preset = preset, selected = preset.id == selected, onPick = onPick)
        }
    }
}

@Composable
private fun PCategoryChip(preset: CategoryPreset, selected: Boolean, onPick: (String) -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg, RoundedCornerShape(16.dp))
            .clickable { onPick(preset.id) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(preset.emoji, fontSize = 22.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            preset.name,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 日期按钮 + DatePickerDialog（沿用 M1 交互：点击弹日历，确定回写 LocalDate） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onValue: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text(
            value?.let { "$label：${dateZh(it, LocalDate.now())}" } ?: "$label（点击选择）",
            maxLines = 1,
        )
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
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

/** 图标大按钮：28sp 当前 emoji（null 显示品类回退），点击弹 EmojiPickerSheet */
@Composable
fun EmojiIconButton(display: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(display, fontSize = 28.sp)
    }
}

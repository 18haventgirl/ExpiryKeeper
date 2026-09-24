package com.expirykeeper.core.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 快速操作三件套：今日屏与详情浮层共用一份（此前两处各写一遍，且用 emoji 当功能图标）。
 * 传 null 表示该动作对当前物品不适用（例如没有提醒快照时不能「稍后」或「不再提醒」）。
 */
@Composable
fun QuickActions(
    onRollForward: () -> Unit,
    modifier: Modifier = Modifier,
    onSnooze: (() -> Unit)?,
    onHandle: (() -> Unit)?,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionChip(Icons.Filled.Autorenew, "续期", onRollForward)
        onSnooze?.let { ActionChip(Icons.Filled.Snooze, "稍后3天", it) }
        onHandle?.let { ActionChip(Icons.Filled.CheckCircle, "今天不再提醒", it) }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}

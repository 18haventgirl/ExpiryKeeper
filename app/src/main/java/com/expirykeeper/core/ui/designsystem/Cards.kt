package com.expirykeeper.core.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.domain.DueStatus
import com.expirykeeper.core.domain.RingSpec

/** 状态色对：容器色 + 内容色（取代 Pair，字段名自解释） */
data class Tone(val container: Color, val content: Color)

/** DueStatus → M3 容器/内容色映射，全应用状态配色的唯一来源 */
@Composable
fun StatusTone(status: DueStatus): Tone = when (status) {
    DueStatus.OVERDUE, DueStatus.DUE_TODAY ->
        Tone(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    DueStatus.DUE_SOON, DueStatus.RENEWAL_SOON ->
        Tone(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    DueStatus.LOW_STOCK ->
        Tone(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    DueStatus.RENEWAL_TODAY ->
        Tone(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
}

/** 大圆角胶囊状态标签 */
@Composable
fun StatusPill(status: DueStatus, label: String, modifier: Modifier = Modifier) {
    val tone = StatusTone(status)
    Box(
        modifier = modifier
            .background(tone.container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = tone.content,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * 到期环：只渲染 `ringSpec` 算出的文本与弧，配色跟随状态 tone。
 * 修 A4：此前恒用 primary + daysLeft/14，逾期与「今天到期」长得一模一样。
 * 文本超过两位时降字号，避免 44dp 圆内裁字（如逾期 200 天）。
 */
@Composable
fun DueRing(spec: RingSpec, tone: Tone, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val textStyle = if (spec.text.length <= 2) {
        MaterialTheme.typography.headlineSmall
    } else {
        MaterialTheme.typography.labelLarge
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            // 注意：此处 size 被 DrawScope.size 遮蔽，取 Dp 参数用外层 ringPx
            val dim = this.size.minDimension
            val strokeW = dim * 0.12f
            val arcSize = Size(dim - strokeW, dim - strokeW)
            val topLeft = Offset(strokeW / 2f, strokeW / 2f)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
            if (spec.fraction > 0f) {
                drawArc(
                    color = tone.content, startAngle = -90f, sweepAngle = 360f * spec.fraction,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
        }
        Text(spec.text, style = textStyle, color = tone.content)
    }
}

/**
 * 清单/今日通用条目卡：large 圆角 + surfaceContainer + 按压 scale 1→0.97 动画；
 * leading 44dp 圆形 tone 底 emoji 文本（26sp），trailing 槽放 StatusPill / DueRing 等。
 */
@Composable
fun ItemCard(
    item: Item,
    icon: String,
    tone: Tone,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "itemCardPress")
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        val clickModifier = when {
            onLongClick != null -> Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick ?: {},
                onLongClick = onLongClick,
            )
            onClick != null -> Modifier.clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            else -> Modifier
        }
        Row(
            modifier = clickModifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(tone.container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 22.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                val subtitle = item.location ?: item.note
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** emoji 单选行：FlowRow 胶囊 chip，选中 secondaryContainer */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiRow(
    selected: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { emoji ->
            val isSelected = emoji == selected
            val bg = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg, RoundedCornerShape(50))
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(50),
                    )
                    .clickable { onSelect(emoji) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 22.sp, color = contentColor)
            }
        }
    }
}

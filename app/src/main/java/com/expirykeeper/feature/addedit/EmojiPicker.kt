package com.expirykeeper.feature.addedit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.EmojiSet
import com.expirykeeper.core.ui.designsystem.EmojiRow

/**
 * emoji 选择弹层（Task 10）：首项"跟随品类"清空物品级 emoji（onPick(null)），
 * 随后当前品类组 + other 组，复用设计系统 EmojiRow；点选即回调，选后关闭由父级决定。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmojiPickerSheet(
    current: String?,
    category: String,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val cat = Categories.byId(category)
    val own = EmojiSet.forCategory(category)
    val others = EmojiSet.othersExcluding(category)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("选择图标", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onPick(null) }) {
                if (current == null) {
                    Icon(Icons.Filled.Check, contentDescription = "当前选中")
                    Spacer(Modifier.width(8.dp))
                }
                Text("跟随品类 ${cat?.emoji ?: "📦"}")
            }
            Text(
                // 括号而不是「·」：品类名自己含 ·，两种语义撞在同一个符号上（修 B11）
                "常用（${cat?.name ?: "本品类"}）",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            EmojiRow(selected = current, options = own, onSelect = onPick)
            if (others.isNotEmpty()) {
                Text(
                    "更多",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                EmojiRow(selected = current, options = others, onSelect = onPick)
            }
        }
    }
}

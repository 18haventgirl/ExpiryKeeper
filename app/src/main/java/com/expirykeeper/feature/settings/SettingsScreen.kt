package com.expirykeeper.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expirykeeper.ui.ItemsViewModel

/** 设置壳：本任务只落备份/恢复卡（Task 11 完整体）。样式先走 Material3 默认，Task 7 设计系统统一。 */
@Composable
fun SettingsScreen(vm: ItemsViewModel, onBack: () -> Unit) {
    var status by remember { mutableStateOf("尚未执行备份或恢复操作") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { vm.exportBackup(it) { result ->
            result.fold(
                onSuccess = { count -> status = "已导出 $count 条数据" },
                onFailure = { e -> status = "导出失败：${e.message}" },
            )
        } }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.importBackup(it) { result ->
            result.fold(
                onSuccess = { (written, skipped) ->
                    status = "已恢复 $written 条，跳过 $skipped 条（本地更新的数据不会被旧备份覆盖）"
                },
                onFailure = { status = "备份文件格式不对，未导入任何数据" },
            )
        } }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "导出为 JSON 文件；恢复时与本地数据按最后写入时间合并，绝不清空现有数据。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("expiry-keeper-backup.json") }) {
                            Text("导出备份")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Text("导入恢复")
                        }
                    }
                    Spacer(Modifier.width(1.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

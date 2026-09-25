package com.expirykeeper.feature.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expirykeeper.App
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.domain.BackupFormatException
import com.expirykeeper.core.domain.dateZh
import com.expirykeeper.core.domain.nextDailyFire
import com.expirykeeper.core.domain.RestorePlan
import com.expirykeeper.core.ui.designsystem.BigHeader
import com.expirykeeper.core.ui.designsystem.EkCard
import com.expirykeeper.core.ui.designsystem.KeyValueRow
import com.expirykeeper.notifications.ReminderScheduler
import com.expirykeeper.ui.ItemsViewModel
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 设置完整体（Task 11）：权限状态 / 外观（动态取色）/ 数据（备份迁移至此，SAF 机制沿用 Task 6）/
 * 概览 / 关于。权限行点击跳系统设置页；状态在每次 ON_RESUME 重读（用户可能刚从系统页返回）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: ItemsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as App).container.prefs }

    // 每日提醒时刻：本地状态只为让卡片在改完后立刻重算"下次提醒"那一行
    var reminderHour by remember { mutableIntStateOf(prefs.reminderHour) }
    var reminderMinute by remember { mutableIntStateOf(prefs.reminderMinute) }

    // ON_RESUME 计数：驱动权限状态与事件计数重读
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 卡1：通知权限（API33+ 才需要运行时授权，33 以下安装即授予）
    val notificationsGranted = remember(resumeTick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    // 精确闹钟：API31+ 真实查询；29~30 系统默认允许精确 Alarm
    val exactAlarmsAllowed = remember(resumeTick) {
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }
    fun openSystemSettings(action: String) {
        val pkgUri = Uri.parse("package:${context.packageName}")
        runCatching {
            context.startActivity(
                Intent(action)
                    .setData(pkgUri)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    // 卡2：动态取色 —— 写 prefs 后 recreate Activity 整体换色（修复 T7 一次性读取）
    var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }

    // 卡4：概览计数
    val items by vm.items.collectAsStateWithLifecycle()
    var rollCount by remember { mutableStateOf(0) }
    LaunchedEffect(resumeTick, items) { rollCount = vm.rollCount30d() }

    // 卡3：备份（busy 守卫：IO 进行时禁用两个按钮）
    var status by remember { mutableStateOf("尚未执行备份或恢复操作") }
    var busy by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            vm.exportBackup(uri) { result ->
                busy = false
                result.fold(
                    onSuccess = { count -> status = "导出成功 $count 条" },
                    onFailure = { status = "导出失败，未写入任何数据" },
                )
            }
        } else {
            busy = false
        }
    }
    // 导入分两步：先只读地算出恢复计划，确认框点头之后才写库
    var pendingPlan by remember { mutableStateOf<RestorePlan?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.inspectBackup(uri) { result ->
                busy = false
                result.fold(
                    onSuccess = { plan ->
                        if (plan == null) status = "这份备份里没有任何条目，已取消" else pendingPlan = plan
                    },
                    onFailure = { e ->
                        status = if (e is BackupFormatException) "备份文件格式不对，未导入任何数据" else "读取文件失败"
                    },
                )
            }
        } else {
            busy = false
        }
    }

    // 外层 EkApp Scaffold 已处理系统栏 inset，此处不再嵌套 Scaffold（双 padding 会让标题下沉）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BigHeader(title = "设置", subtitle = "提醒权限 · 外观 · 数据", onBack = onBack)

        EkCard("提醒权限状态") {
            PermissionRow(
                ok = notificationsGranted,
                title = "通知权限",
                descOn = "已授权，提醒可正常弹出",
                descOff = "未授权，通知不会显示 · 点击去开启",
                onClick = { if (!notificationsGranted) openSystemSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) },
            )
            PermissionRow(
                ok = exactAlarmsAllowed,
                title = "精确闹钟",
                descOn = if (Build.VERSION.SDK_INT >= 31) "已允许，提醒将准时触发" else "当前系统默认允许",
                descOff = "未允许，提醒可能被系统延迟 · 点击去设置",
                onClick = {
                    if (exactAlarmsAllowed || Build.VERSION.SDK_INT < 31) Unit else
                        openSystemSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                },
            )
        }

        // 卡1b：每日提醒时刻。此前 ReminderScheduler 写死 9:00 且界面上根本不提，
        // 用户既改不了也不知道几点会被打扰。
        ReminderTimeCard(
            hour = reminderHour,
            minute = reminderMinute,
            exactAlarmsAllowed = exactAlarmsAllowed,
            onConfirm = { h, m ->
                reminderHour = h
                reminderMinute = m
                prefs.reminderHour = h
                prefs.reminderMinute = m
                // 闹钟是一次性的，改完必须重排，否则还在老点响
                ReminderScheduler.schedule(context)
            },
        )

        EkCard("外观") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "跟随壁纸生成配色，关闭后回落品牌 teal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = {
                        dynamicColor = it
                        vm.setDynamicColor(it)
                        context.findActivity()?.recreate()
                    },
                )
            }
        }

        EkCard("数据") {
            Text(
                "导出为 JSON 文件（含已删除记录）。导入 = 时间点还原：清单会变成导出那一刻的样子，" +
                    "备份之后新增的物品会被移出。恢复前会先让你确认，并且可以当场撤销。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        status = "正在处理…"
                        exportLauncher.launch("expiry-keeper-backup.json")
                    },
                ) { Text("导出备份") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        status = "正在处理…"
                        importLauncher.launch(arrayOf("application/json"))
                    },
                ) { Text("导入恢复") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 整库替换是不可逆到"下一次导入"为止的破坏性操作：动手之前必须把账目摊开确认
        pendingPlan?.let { plan ->
            AlertDialog(
                onDismissRequest = { pendingPlan = null },
                title = { Text("用这份备份替换全部物品？") },
                text = {
                    Column {
                        Text("写回 ${plan.liveWritten} 条物品" + if (plan.tombstones > 0) "，重放 ${plan.tombstones} 条删除" else "")
                        if (plan.removed.isNotEmpty()) {
                            Text(
                                "清单里 ${plan.removed.size} 件不在这份备份中，将被移出：" +
                                    plan.removed.joinToString("、") { it.name }.takeIf { plan.removed.size <= 4 }
                                        .orEmpty(),
                            )
                        }
                        Text("移出只是暂时收起，不是真删；恢复后当下也能在提示条上点「撤销」全部换回来。")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingPlan = null
                            busy = true
                            vm.applyRestore(plan) { r ->
                                busy = false
                                status = r.fold(
                                    onSuccess = { "已恢复为备份时的状态" },
                                    onFailure = { "恢复失败，清单未改动" },
                                )
                            }
                        },
                    ) { Text("替换全部") }
                },
                dismissButton = { TextButton(onClick = { pendingPlan = null }) { Text("取消") } },
            )
        }

        EkCard("概览") {
            KeyValueRow("总件数", "${items.size} 件")
            KeyValueRow("30 天处理次数", "$rollCount 次")
            Text("品类分布", style = MaterialTheme.typography.labelLarge)
            val grouped = items.groupingBy { it.categoryId }.eachCount()
            if (grouped.isEmpty()) {
                Text("暂无物品", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Categories.all
                        .filter { grouped.containsKey(it.id) }
                        .forEach { c -> Text("${c.emoji}×${grouped.getValue(c.id)}", style = MaterialTheme.typography.bodyMedium) }
                    grouped.filterKeys { Categories.byId(it) == null }.values.forEach { n ->
                        Text("❓×$n", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        EkCard("关于") {
            Text("到期管家（工程版）", style = MaterialTheme.typography.titleMedium)
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "未知"
            }
            Text("版本 $version", style = MaterialTheme.typography.bodyMedium)
            Text(
                "本地优先的到期提醒管家：数据只存在本机，删除可撤销，备份可迁移。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}


/**
 * 每日提醒时刻卡。单独成 composable 是因为 M3 的 TimePicker 还带
 * `@ExperimentalMaterial3Api`——opt-in 关在这一个组件里，不污染整个设置页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeCard(
    hour: Int,
    minute: Int,
    exactAlarmsAllowed: Boolean,
    onConfirm: (Int, Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    EkCard("每日提醒") {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("检查时间", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "到点才发通知；没到点时改数据不会提前弹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.titleMedium)
        }
        // 把"下一次是什么时候"写出来：只报一个 HH:mm，用户无法判断是今天还是明天
        val next = nextDailyFire(LocalDateTime.now(), hour, minute)
        Text(
            "下次提醒：${dateZh(next.toLocalDate(), LocalDate.now())} %02d:%02d".format(next.hour, next.minute),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!exactAlarmsAllowed && Build.VERSION.SDK_INT >= 31) {
            Text(
                "未允许精确闹钟，实际触发可能延到最长 24 小时的兜底扫描",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (picking) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("每日提醒时间") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(state.hour, state.minute)
                        picking = false
                    },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PermissionRow(ok: Boolean, title: String, descOn: String, descOff: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ok) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态用 Material 图标而不是 ✅/⚠️：emoji 只靠图形传达，读屏拿不到语义（修 B9）
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = if (ok) "已开启" else "未开启",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (ok) descOn else descOff,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}


/** 从 Composable 的 Context 层层解包找到宿主 Activity（recreate 用） */
private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

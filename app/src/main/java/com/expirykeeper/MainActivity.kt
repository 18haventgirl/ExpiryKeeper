package com.expirykeeper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.expirykeeper.core.ui.designsystem.EkTheme
import com.expirykeeper.notifications.ReminderScheduler

class MainActivity : ComponentActivity() {

    private val askNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ReminderScheduler.runNow(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 37 下平台已强制边到边；显式声明才能拿到系统栏图标配色（auto 跟随日/夜），
        // 否则暗色动态主题下状态栏图标可能与背景同色（修 B13）
        enableEdgeToEdge()
        // 仅在「尚未授权」时请求；已授权用户冷启动不再触发 granted→runNow→notifyAll，
        // 否则用户刚滑掉的通知会在打开 App 几秒后原样复活（表现为"通知不会自动消失"）
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!notificationGranted) {
            askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val dynamicAllowed = (application as App).container.prefs.dynamicColor
        setContent {
            EkTheme(dynamicAllowed = dynamicAllowed) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier) {
                    EkApp()
                }
            }
        }
    }
}

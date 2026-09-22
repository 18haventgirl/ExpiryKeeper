package com.expirykeeper

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        if (Build.VERSION.SDK_INT >= 33) {
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

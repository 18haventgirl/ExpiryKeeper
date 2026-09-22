package com.expirykeeper.core.ui.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 应用主题：API31+ 且 [dynamicAllowed] 开 → Material You 动态取色；否则回落品牌 teal。
 * 开关来源：MainActivity 读 app.container.prefs.dynamicColor 后传入。
 */
@Composable
fun EkTheme(dynamicAllowed: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (dynamicAllowed && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        DarkColors
    } else {
        LightColors
    }
    MaterialTheme(colorScheme = scheme, shapes = EkShapes, typography = EkTypography, content = content)
}

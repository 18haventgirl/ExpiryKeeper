package com.expirykeeper.core.ui.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** 品牌 teal 色板（沿用 v1 Theme.kt 数值，动态取色不可用时的回落） */
private val Teal80 = Color(0xFF4FD8DC)
private val Teal40 = Color(0xFF00696D)
private val SlateTeal40 = Color(0xFF4B6364)
private val SlateTeal80 = Color(0xFFB0CCCD)
private val Amber40 = Color(0xFF8A4E00)
private val Amber80 = Color(0xFFFFB870)

val LightColors = lightColorScheme(
    primary = Teal40,
    secondary = SlateTeal40,
    tertiary = Amber40,
)

val DarkColors = darkColorScheme(
    primary = Teal80,
    secondary = SlateTeal80,
    tertiary = Amber80,
)

package com.expirykeeper.core.data

import android.content.Context

/** 应用偏好读写（包 SharedPreferences），UI 直接读，写时即时生效 */
class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 动态取色（Material You）开关，仅 API31+ 实际生效，默认开 */
    var dynamicColor: Boolean
        get() = p.getBoolean("dynamicColor", true)
        set(v) = p.edit().putBoolean("dynamicColor", v).apply()
}

package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item

/** 到期规则的两种录入方式（表单态，不落库） */
enum class ExpiryFormMode { DATE, OPENED }

/**
 * 到期表单的模式判定与互斥清理（纯函数，供 AddEditScreen 与单测共用）。
 * 语义：两模式互斥——date 模式只留 expireAt，opened 模式只留 openedAt+shelfLife，
 * 到期日由 ItemRepository.save 统一推导，UI 不自行计算写库。
 */
object ExpiryForm {

    /** 自定义保质期合法区间（天）；品类预设 chips 也按此过滤超界值 */
    val ShelfLifeRange = 1..3650

    /** 回填判定：无显式到期日但有保质期 → 开封模式；其余（含 M1 老数据双 null）→ 日期模式 */
    fun modeOf(item: Item): ExpiryFormMode =
        if (item.expireAtEpochDay == null && item.shelfLifeDays != null) ExpiryFormMode.OPENED
        else ExpiryFormMode.DATE

    /** 日期模式落库形态：清空开封/保质期字段 */
    fun applyDateMode(item: Item, expireAtEpochDay: Long?): Item =
        item.copy(expireAtEpochDay = expireAtEpochDay, openedAtEpochDay = null, shelfLifeDays = null)

    /** 开封模式落库形态：清空显式到期日（repo.save 读 openedAt+shelfLife 派生） */
    fun applyOpenedMode(item: Item, openedAtEpochDay: Long?, shelfLifeDays: Int?): Item =
        item.copy(expireAtEpochDay = null, openedAtEpochDay = openedAtEpochDay, shelfLifeDays = shelfLifeDays)

    /** 自定义天数文本 → 合法整数或 null（非数字/越界一律 null，由 UI 显示行内错误） */
    fun parseDays(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in ShelfLifeRange }

    /** 切换后是否属于"两模式之一已有有效值"（保存前置校验） */
    fun hasValidExpiry(mode: ExpiryFormMode, expireAtEpochDay: Long?, openedAtEpochDay: Long?, shelfLifeDays: Int?): Boolean =
        when (mode) {
            ExpiryFormMode.DATE -> expireAtEpochDay != null
            ExpiryFormMode.OPENED -> openedAtEpochDay != null && shelfLifeDays != null
        }
}

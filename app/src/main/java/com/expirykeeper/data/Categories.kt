package com.expirykeeper.data

/** 内置品类：默认提醒语义 + 保质期常识（M2 扩充为用户可编辑模板） */
data class CategoryPreset(
    val id: String,
    val name: String,
    val emoji: String,
    val reminderKind: ReminderKind,
    val defaultOffsetsDays: List<Int>,
    /** 录入时的保质期快捷天数选项（基于开封/购买常识） */
    val defaultShelfLifeChoicesDays: List<Int>,
    val shelfLifeHint: String,
)

object Categories {
    val all = listOf(
        CategoryPreset("food-chilled", "食材·冷藏", "🥛", ReminderKind.EXPIRY, listOf(2, 0), listOf(1, 3, 5, 7), "开封牛奶约3天，熟食约3~4天"),
        CategoryPreset("food-frozen", "食材·冷冻", "🧊", ReminderKind.EXPIRY, listOf(14, 7, 0), listOf(30, 90, 180, 365), "肉类冷冻约3~6个月"),
        CategoryPreset("food-dry", "干货·零食", "🍪", ReminderKind.EXPIRY, listOf(14, 7, 0), listOf(30, 90, 180, 365), "以包装日期为准"),
        CategoryPreset("medicine", "药品保健", "💊", ReminderKind.EXPIRY, listOf(60, 30, 7), listOf(180, 365, 730), "开封糖浆/眼药水约4周"),
        CategoryPreset("cosmetic", "化妆品·个护", "🧴", ReminderKind.EXPIRY, listOf(30, 7, 0), listOf(180, 365), "开封后参照瓶身开盖图标(6M/12M)"),
        CategoryPreset("warranty", "数码·保修", "🔌", ReminderKind.EXPIRY, listOf(60, 30, 7), listOf(365, 730), "按购机发票日期"),
        CategoryPreset("document", "证件·证照", "🪪", ReminderKind.EXPIRY, listOf(90, 30, 7), listOf(3650, 3650 * 5, 3650 * 10), "驾照/护照到期前90天可换"),
        CategoryPreset("subscription", "会员·订阅", "🔁", ReminderKind.RECURRING, listOf(3, 0), listOf(30, 90, 365), "续费前3天提醒"),
        CategoryPreset("household", "家居耗材", "🧻", ReminderKind.CONSUMABLE, listOf(0), listOf(), "滤芯/垃圾袋等低库存提醒"),
    )

    fun byId(id: String): CategoryPreset? = all.firstOrNull { it.id == id }
    fun default(id: String): CategoryPreset = byId(id) ?: all.first()
}

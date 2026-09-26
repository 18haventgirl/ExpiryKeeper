package com.expirykeeper.core.data

/**
 * 常见物品模板：只是**录入加速器**，点一下预填名称/emoji/天数，用户可随意改。
 * 不落库（物品不记得自己用过哪个模板），所以改这里不需要数据迁移。
 *
 * days 的含义随所属品类的 reminderKind 变化：EXPIRY=保质期天数、RECURRING=续费周期天数、
 * CONSUMABLE=预计使用天数（当前该类型没有时间维度，planFill 会忽略它，留作换新一代耗材的参考）。
 */
data class SubCategory(
    val name: String,
    val emoji: String,
    val days: Int?,
)

/** 内置品类：默认提醒语义 + 保质期常识 + 常见物品模板 */
data class CategoryPreset(
    val id: String,
    val name: String,
    val emoji: String,
    val reminderKind: ReminderKind,
    val defaultOffsetsDays: List<Int>,
    /** 录入时的保质期快捷天数选项（基于开封/购买常识） */
    val defaultShelfLifeChoicesDays: List<Int>,
    val shelfLifeHint: String,
    val subcategories: List<SubCategory> = emptyList(),
)

object Categories {
    val all = listOf(
        CategoryPreset(
            "food-chilled", "食材·冷藏", "🥛", ReminderKind.EXPIRY, listOf(2, 0), listOf(1, 3, 5, 7),
            "开封牛奶约3天，熟食约3~4天",
            subcategories = listOf(
                SubCategory("鲜牛奶", "🥛", 3),
                SubCategory("熟食·剩菜", "🍗", 3),
                SubCategory("叶菜", "🥬", 5),
                SubCategory("豆腐", "🥡", 5),
                SubCategory("酸奶", "🥤", 14),
                SubCategory("鸡蛋", "🥚", 30),
                SubCategory("鲜肉", "🥩", 2),
            ),
        ),
        CategoryPreset(
            "food-frozen", "食材·冷冻", "🧊", ReminderKind.EXPIRY, listOf(14, 7, 0), listOf(30, 90, 180, 365),
            "肉类冷冻约3~6个月",
            subcategories = listOf(
                SubCategory("速冻饺子", "🥟", 180),
                SubCategory("冻肉", "🥩", 180),
                SubCategory("冻海鲜", "🦐", 90),
                SubCategory("冰淇淋", "🍦", 90),
            ),
        ),
        CategoryPreset(
            "food-dry", "干货·零食", "🍪", ReminderKind.EXPIRY, listOf(14, 7, 0), listOf(30, 90, 180, 365),
            "以包装日期为准",
            subcategories = listOf(
                SubCategory("大米", "🍚", 180),
                SubCategory("面粉", "🌾", 180),
                SubCategory("坚果", "🥜", 90),
                SubCategory("饼干", "🍪", 120),
                SubCategory("食用油", "🫗", 540),
                SubCategory("调味料", "🧂", 365),
            ),
        ),
        CategoryPreset(
            "medicine", "药品保健", "💊", ReminderKind.EXPIRY, listOf(60, 30, 7), listOf(180, 365, 730),
            "开封糖浆/眼药水约4周",
            subcategories = listOf(
                SubCategory("未开封药品", "💊", 730),
                SubCategory("开封糖浆·眼药水", "🧴", 28),
                SubCategory("维生素·鱼油", "🟠", 365),
                SubCategory("创可贴·纱布", "🩹", 1095),
            ),
        ),
        CategoryPreset(
            "cosmetic", "化妆品·个护", "🧴", ReminderKind.EXPIRY, listOf(30, 7, 0), listOf(180, 365),
            "开封后参照瓶身开盖图标(6M/12M)",
            subcategories = listOf(
                SubCategory("面霜·精华", "🧴", 365),
                SubCategory("防晒霜", "☀️", 365),
                SubCategory("睫毛膏·眼线", "👁️", 180),
                SubCategory("洗发水·沐浴露", "🧼", 730),
                SubCategory("香水", "🌸", 1095),
            ),
        ),
        CategoryPreset(
            "warranty", "数码·保修", "🔌", ReminderKind.EXPIRY, listOf(60, 30, 7), listOf(365, 730),
            "按购机发票日期",
            subcategories = listOf(
                SubCategory("手机", "📱", 1095),
                SubCategory("笔记本", "💻", 1095),
                SubCategory("耳机", "🎧", 365),
                SubCategory("小家电", "🔌", 365),
            ),
        ),
        CategoryPreset(
            "document", "证件·证照", "🪪", ReminderKind.EXPIRY, listOf(90, 30, 7), listOf(3650, 3650 * 5, 3650 * 10),
            "驾照/护照到期前90天可换",
            subcategories = listOf(
                SubCategory("护照", "🧭", 1825),
                SubCategory("身份证", "🪪", 3650),
                SubCategory("驾照", "🚗", 6570),
                SubCategory("车辆年检", "🚙", 365),
            ),
        ),
        CategoryPreset(
            "subscription", "会员·订阅", "🔁", ReminderKind.RECURRING, listOf(3, 0), listOf(30, 90, 365),
            "续费前3天提醒",
            subcategories = listOf(
                SubCategory("视频会员", "📺", 30),
                SubCategory("音乐会员", "🎵", 30),
                SubCategory("云存储", "☁️", 365),
                SubCategory("健身卡", "🏋️", 365),
                SubCategory("宽带", "📶", 365),
            ),
        ),
        CategoryPreset(
            "household", "家居耗材", "🧻", ReminderKind.CONSUMABLE, listOf(0), listOf(),
            "滤芯/垃圾袋等低库存提醒",
            subcategories = listOf(
                SubCategory("净水器滤芯", "🚰", 180),
                SubCategory("猫粮", "🐱", 30),
                SubCategory("纸巾", "🧻", 60),
                SubCategory("垃圾袋", "🗑️", 90),
                SubCategory("电池", "🔋", 365),
            ),
        ),
    )

    fun byId(id: String): CategoryPreset? = all.firstOrNull { it.id == id }
    fun default(id: String): CategoryPreset = byId(id) ?: all.first()
}

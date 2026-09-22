package com.expirykeeper.core.data

/**
 * 品类常用 emoji 图集（Task 10 录入选择器）。
 * 放 core/data 而非 core/domain：纯展示常量、与 Categories 预设同层，
 * 避免 ui → domain 为常量反向 import（CODESTYLE §包分层）。
 */
object EmojiSet {
    /** key = CategoryPreset.id + "other" 兜底组；每组 12~16 个，只收录主流设备可渲染的码位 */
    val byCategory: Map<String, List<String>> = mapOf(
        "food-chilled" to listOf("🥛", "🧈", "🥚", "🧀", "🥗", "🍄", "🍗", "🥩", "🍚", "🥫", "🍞", "🥦", "🍓", "🥒"),
        "food-frozen" to listOf("🧊", "🍟", "🥟", "🍣", "🍦", "🥩", "🍗", "🦐", "🐟", "🥬", "🍤", "🥘"),
        "food-dry" to listOf("🍪", "🍜", "🥜", "☕", "🍫", "🥫", "🍝", "🧂", "🌰", "🍵", "🍞", "🍿", "🍘", "🥔"),
        "medicine" to listOf("💊", "🩹", "🧴", "🩺", "💉", "🌡️", "😷", "🧪", "🦷", "🩸", "🩼", "🌿"),
        "cosmetic" to listOf("🧴", "💄", "🧼", "🪮", "🌸", "🪞", "💅", "🌹", "✨", "🌺", "👄", "🫧"),
        "warranty" to listOf("🔌", "📱", "💻", "🖥️", "🎧", "⌚", "🖨️", "📷", "🎮", "🖱️", "⌨️", "🔋", "💾", "📀"),
        "document" to listOf("🪪", "📇", "🛂", "📜", "🏠", "🚗", "📄", "🗂️", "🔖", "💳", "🏛️", "📛", "🔑"),
        "subscription" to listOf("🔁", "📺", "🎬", "🎵", "☁️", "🏋️", "📰", "🎮", "🎧", "📚", "🌐", "🛒", "💪"),
        "household" to listOf("🧻", "🧽", "🧼", "🪣", "🔋", "🧯", "🧹", "💡", "🕯️", "🧺", "🪑", "🚿", "🧴"),
        "other" to listOf("📦", "🎁", "🧸", "🪴", "🔧", "🍂", "⚙️", "🚙", "👕", "📚", "🎸", "🖼️", "🐠", "⚽", "🧶", "🔑"),
    )

    fun forCategory(categoryId: String): List<String> = byCategory[categoryId] ?: byCategory.getValue("other")

    /** "other" 组展示用：去掉与品类组重复的码位，避免同屏双份 */
    fun othersExcluding(categoryId: String): List<String> {
        val own = byCategory[categoryId]?.toSet() ?: emptySet()
        return byCategory.getValue("other").filterNot { it in own }
    }
}

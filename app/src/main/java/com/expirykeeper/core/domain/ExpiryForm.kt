package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import com.expirykeeper.core.data.SubCategory
import java.time.LocalDate

/** 到期规则的两种录入方式（表单态，不落库） */
enum class ExpiryFormMode { DATE, OPENED }

/** 点「常见物品」模板后要写进表单的内容（不落库，纯录入加速器） */
data class TemplateFill(
    val name: String,
    val emoji: String,
    /** EXPIRY=保质期天数；RECURRING=续费周期；CONSUMABLE 恒为 null（该类型没有时间维度） */
    val days: Int?,
    val useOpenedMode: Boolean,
    val openedDate: LocalDate?,
)

/** 规则区当前输入（纯数据）：表单与单测共用同一份校验入口 */
data class RuleState(
    val kind: ReminderKind,
    val mode: ExpiryFormMode = ExpiryFormMode.DATE,
    val expireDate: LocalDate? = null,
    val openedDate: LocalDate? = null,
    val shelfLife: Int? = null,
    val nextDueDate: LocalDate? = null,
    val recurrence: Int? = null,
    val quantityError: String? = null,
    val thresholdError: String? = null,
)

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

    /**
     * 常见物品模板 → 表单预填。三条规则由单测钉住：
     * 名字只在用户还没输入时才填（不覆盖手打的）；CONSUMABLE 不编造保质期；
     * 只有 EXPIRY 才切到「开封+保质期」并把开封日定为今天。
     */
    fun planFill(sub: SubCategory, existingName: String, kind: ReminderKind, today: LocalDate): TemplateFill {
        val shelfLifeApplies = kind == ReminderKind.EXPIRY && sub.days != null
        return TemplateFill(
            name = existingName.ifBlank { sub.name },
            emoji = sub.emoji,
            days = if (kind == ReminderKind.CONSUMABLE) null else sub.days,
            useOpenedMode = shelfLifeApplies,
            openedDate = if (shelfLifeApplies) today else null,
        )
    }

    /** 规则区缺什么：null 表示规则完整（自 AddEditScreen 抽出，修 A3 时一并可测） */
    fun ruleError(s: RuleState): String? = when (s.kind) {
        ReminderKind.EXPIRY -> when {
            s.mode == ExpiryFormMode.DATE && s.expireDate == null -> "请选择到期日期"
            s.mode == ExpiryFormMode.OPENED && s.openedDate == null -> "请选择开封日期"
            s.mode == ExpiryFormMode.OPENED && s.shelfLife == null -> "请选择或输入保质期（1~3650 天）"
            else -> null
        }
        ReminderKind.CONSUMABLE -> s.quantityError ?: s.thresholdError
        // I-4：无效/未选周期走行内错误（与保质期同法），不再在 buildItem 里静默回退旧值
        ReminderKind.RECURRING -> when {
            s.nextDueDate == null -> "请选择下次扣费日期"
            s.recurrence == null -> "请选择或输入续费周期（1~3650 天）"
            else -> null
        }
    }
}

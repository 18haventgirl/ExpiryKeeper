package com.expirykeeper.domain

import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.SubCategory
import com.expirykeeper.core.data.ReminderKind
import com.expirykeeper.core.domain.ExpiryForm
import com.expirykeeper.core.domain.ExpiryFormMode
import com.expirykeeper.core.domain.RuleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpiryFormTest {

    private fun expiryItem(
        expireAt: Long? = null,
        openedAt: Long? = null,
        shelfLife: Int? = null,
    ) = Item(
        id = "form-${expireAt}-${openedAt}-${shelfLife}",
        name = "牛奶",
        categoryId = "food-chilled",
        reminderKind = ReminderKind.EXPIRY,
        expireAtEpochDay = expireAt,
        openedAtEpochDay = openedAt,
        shelfLifeDays = shelfLife,
    )

    @Test fun `opened mode detected only when expire empty and shelf life set`() {
        assertEquals(ExpiryFormMode.OPENED, ExpiryForm.modeOf(expiryItem(openedAt = 100, shelfLife = 3)))
        assertEquals(ExpiryFormMode.DATE, ExpiryForm.modeOf(expiryItem(expireAt = 200, openedAt = 100, shelfLife = 3)))
    }

    @Test fun `legacy M1 item with no new fields falls back to date mode`() {
        assertEquals(ExpiryFormMode.DATE, ExpiryForm.modeOf(expiryItem()))
        assertEquals(ExpiryFormMode.DATE, ExpiryForm.modeOf(expiryItem(expireAt = 200)))
    }

    @Test fun `applyDateMode clears opened fields`() {
        val out = ExpiryForm.applyDateMode(expiryItem(openedAt = 100, shelfLife = 3), expireAtEpochDay = 200)
        assertEquals(200L, out.expireAtEpochDay)
        assertNull(out.openedAtEpochDay)
        assertNull(out.shelfLifeDays)
    }

    @Test fun `applyOpenedMode clears explicit expire`() {
        val out = ExpiryForm.applyOpenedMode(expiryItem(expireAt = 200), openedAtEpochDay = 100, shelfLifeDays = 3)
        assertNull(out.expireAtEpochDay)
        assertEquals(100L, out.openedAtEpochDay)
        assertEquals(3, out.shelfLifeDays)
    }

    @Test fun `parseDays enforces range 1 to 3650 and integer text`() {
        assertEquals(3, ExpiryForm.parseDays(" 3 "))
        assertEquals(3650, ExpiryForm.parseDays("3650"))
        assertNull(ExpiryForm.parseDays("0"))
        assertNull(ExpiryForm.parseDays("3651"))
        assertNull(ExpiryForm.parseDays("abc"))
        assertNull(ExpiryForm.parseDays("2.5"))
        assertNull(ExpiryForm.parseDays(""))
    }

    // ---- A3：规则区缺什么，从 AddEditScreen 抽成可测纯函数 ----

    @Test fun `date mode missing expire date is the only rule error`() {
        assertEquals("请选择到期日期", ExpiryForm.ruleError(RuleState(ReminderKind.EXPIRY)))
        assertNull(ExpiryForm.ruleError(RuleState(ReminderKind.EXPIRY, expireDate = LocalDate.of(2026, 10, 1))))
    }

    @Test fun `opened mode needs opened date before shelf life`() {
        val opened = LocalDate.of(2026, 9, 20)
        assertEquals(
            "请选择开封日期",
            ExpiryForm.ruleError(RuleState(ReminderKind.EXPIRY, ExpiryFormMode.OPENED, shelfLife = 3)),
        )
        assertEquals(
            "请选择或输入保质期（1~3650 天）",
            ExpiryForm.ruleError(RuleState(ReminderKind.EXPIRY, ExpiryFormMode.OPENED, openedDate = opened)),
        )
        assertNull(
            ExpiryForm.ruleError(
                RuleState(ReminderKind.EXPIRY, ExpiryFormMode.OPENED, openedDate = opened, shelfLife = 3),
            ),
        )
    }

    @Test fun `consumable surfaces quantity error ahead of threshold error`() {
        assertEquals(
            "数量必须是大于 0 的数字",
            ExpiryForm.ruleError(
                RuleState(ReminderKind.CONSUMABLE, quantityError = "数量必须是大于 0 的数字", thresholdError = "低库存线无效"),
            ),
        )
        assertEquals(
            "低库存线无效",
            ExpiryForm.ruleError(RuleState(ReminderKind.CONSUMABLE, thresholdError = "低库存线无效")),
        )
        assertNull(ExpiryForm.ruleError(RuleState(ReminderKind.CONSUMABLE)))
    }

    @Test fun `recurring needs next due date then recurrence`() {
        val due = LocalDate.of(2026, 10, 1)
        assertEquals("请选择下次扣费日期", ExpiryForm.ruleError(RuleState(ReminderKind.RECURRING)))
        assertEquals(
            "请选择或输入续费周期（1~3650 天）",
            ExpiryForm.ruleError(RuleState(ReminderKind.RECURRING, nextDueDate = due)),
        )
        assertNull(ExpiryForm.ruleError(RuleState(ReminderKind.RECURRING, nextDueDate = due, recurrence = 30)))
    }

    // ---- M2 常识库：子品类模板只是录入加速器，绝不落库 ----

    private val day = LocalDate.of(2026, 9, 24)

    @Test fun `template fills name only when the user has not typed one`() {
        assertEquals("鲜牛奶", ExpiryForm.planFill(SubCategory("鲜牛奶", "🥛", 3), "", ReminderKind.EXPIRY, day).name)
        val typed = ExpiryForm.planFill(SubCategory("鲜牛奶", "🥛", 3), "特仑苏", ReminderKind.EXPIRY, day)
        assertEquals("特仑苏", typed.name)
        assertEquals("🥛", typed.emoji)
    }

    @Test fun `expiry template switches to opened-plus-shelf-life`() {
        val fill = ExpiryForm.planFill(SubCategory("鲜牛奶", "🥛", 3), "", ReminderKind.EXPIRY, day)
        assertEquals(3, fill.days)
        assertTrue(fill.useOpenedMode)
        assertEquals(day, fill.openedDate)
    }

    @Test fun `recurring template fills the billing cycle not a shelf life`() {
        val fill = ExpiryForm.planFill(SubCategory("视频会员", "📺", 30), "", ReminderKind.RECURRING, day)
        assertEquals(30, fill.days)
        assertTrue(!fill.useOpenedMode)
        assertNull(fill.openedDate)
    }

    @Test fun `consumable template never invents a shelf life`() {
        val fill = ExpiryForm.planFill(SubCategory("净水器滤芯", "🚰", 180), "", ReminderKind.CONSUMABLE, day)
        assertNull(fill.days)
        assertTrue(!fill.useOpenedMode)
        assertEquals("净水器滤芯", fill.name)
    }

    @Test fun `template without days only fills identity`() {
        val fill = ExpiryForm.planFill(SubCategory("某物", "📦", null), "", ReminderKind.EXPIRY, day)
        assertNull(fill.days)
        assertTrue(!fill.useOpenedMode)
        assertNull(fill.openedDate)
    }

    @Test fun `subcategory table stays sane`() {
        Categories.all.forEach { cat ->
            assertTrue("${cat.id} 子品类过多", cat.subcategories.size <= 8)
            val names = cat.subcategories.map { it.name }
            assertEquals("${cat.id} 子品类重名", names.size, names.distinct().size)
            cat.subcategories.forEach {
                assertTrue("${cat.id}/${it.name} 名字为空", it.name.isNotBlank())
                assertTrue("${cat.id}/${it.name} 缺 emoji", it.emoji.isNotBlank())
            }
        }
    }
}

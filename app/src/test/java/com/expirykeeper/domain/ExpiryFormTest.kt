package com.expirykeeper.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import com.expirykeeper.core.domain.ExpiryForm
import com.expirykeeper.core.domain.ExpiryFormMode
import com.expirykeeper.core.domain.RuleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}

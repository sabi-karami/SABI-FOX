package com.sabifox.vpn.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountSlotPolicyTest {
    @Test fun slotBoundaries() {
        assertEquals(10, AccountSlotPolicy.MAX_ACCOUNTS)
        assertFalse(AccountSlotPolicy.validSlot(-1))
        for (i in 0..9) assertTrue(AccountSlotPolicy.validSlot(i))
        assertFalse(AccountSlotPolicy.validSlot(10))
    }

    @Test fun duplicateDetectedAcrossEverySlotCaseInsensitively() {
        for (slot in 0..9) for (other in 0..9) {
            val emails = MutableList<String?>(10) { null }
            emails[other] = "test@example.test"
            assertEquals(slot != other, AccountSlotPolicy.isDuplicate(slot, "TEST@example.test", emails))
        }
    }

    @Test fun distinctAndEmptyOtherSlotsAllowed() {
        val emails = MutableList<String?>(10) { null }
        emails[0] = "first@example.test"
        assertFalse(AccountSlotPolicy.isDuplicate(9, "second@example.test", emails))
        assertFalse(AccountSlotPolicy.isDuplicate(0, "first@example.test", emails))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSlot() { AccountSlotPolicy.isDuplicate(10, "a@example.test", List(10) { null }) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongAccountListLength() { AccountSlotPolicy.isDuplicate(0, "a@example.test", emptyList()) }
}

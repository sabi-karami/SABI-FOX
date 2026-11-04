package com.sabifox.vpn.data

/** Pure policy for account slot validation and duplicate detection. */
internal object AccountSlotPolicy {
    const val MAX_ACCOUNTS = 10
    fun validSlot(slot: Int): Boolean = slot in 0 until MAX_ACCOUNTS
    fun isDuplicate(slot: Int, email: String, existing: List<String?>): Boolean {
        require(validSlot(slot))
        require(existing.size == MAX_ACCOUNTS)
        return existing.withIndex().any { (index, value) ->
            index != slot && email.equals(value, ignoreCase = true)
        }
    }
}

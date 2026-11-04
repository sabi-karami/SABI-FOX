package com.sabifox.vpn.platform

/** Monotonic clock compatible with android.os.SystemClock. */
object SystemClock {
    private val origin = System.nanoTime()

    fun elapsedRealtime(): Long = (System.nanoTime() - origin) / 1_000_000L + BOOT_OFFSET_MS

    fun uptimeMillis(): Long = elapsedRealtime()

    // Keeps elapsed values positive and comfortably above zero, like on Android.
    private const val BOOT_OFFSET_MS = 60_000L
}

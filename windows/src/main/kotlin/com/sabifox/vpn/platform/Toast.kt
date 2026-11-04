package com.sabifox.vpn.platform

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Short messages shown as a snackbar at the bottom of the window (Android Toast equivalent). */
object UiMessages {
    private val mutable =
        MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = mutable

    fun show(text: CharSequence) {
        if (text.isNotBlank()) mutable.tryEmit(text.toString())
    }
}

class Toast private constructor(private val text: CharSequence) {
    fun show() = UiMessages.show(text)

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @Suppress("UNUSED_PARAMETER")
        fun makeText(context: Context?, text: CharSequence, duration: Int): Toast = Toast(text)
    }
}

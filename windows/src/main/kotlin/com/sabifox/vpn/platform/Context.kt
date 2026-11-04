package com.sabifox.vpn.platform

import java.io.File

/**
 * A minimal stand-in for android.content.Context so the shared data layer runs unchanged on
 * Windows. There is exactly one context: the application (see SabiFoxApp).
 */
open class Context internal constructor() {
    open val applicationContext: Context
        get() = this

    val packageName: String
        get() = APPLICATION_ID

    val filesDir: File
        get() = AppPaths.files

    val cacheDir: File
        get() = AppPaths.cache

    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int): SharedPreferences =
        JsonSharedPreferences.open(File(AppPaths.prefs, "$name.json"))

    companion object {
        const val MODE_PRIVATE = 0
        const val APPLICATION_ID = "com.sabifox.vpn"
    }
}

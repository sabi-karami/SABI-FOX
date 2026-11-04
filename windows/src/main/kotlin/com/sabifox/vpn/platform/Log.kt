package com.sabifox.vpn.platform

/** android.util.Log stand-in: writes to stderr, which the launcher discards in release builds. */
object Log {
    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        System.err.println("$level/$tag: $message")
        error?.printStackTrace()
    }

    fun v(tag: String, message: String, error: Throwable? = null): Int { write("V", tag, message, error); return 0 }
    fun d(tag: String, message: String, error: Throwable? = null): Int { write("D", tag, message, error); return 0 }
    fun i(tag: String, message: String, error: Throwable? = null): Int { write("I", tag, message, error); return 0 }
    fun w(tag: String, message: String, error: Throwable? = null): Int { write("W", tag, message, error); return 0 }
    fun w(tag: String, error: Throwable?): Int { write("W", tag, "", error); return 0 }
    fun e(tag: String, message: String, error: Throwable? = null): Int { write("E", tag, message, error); return 0 }
}

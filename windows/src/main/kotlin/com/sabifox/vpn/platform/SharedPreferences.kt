package com.sabifox.vpn.platform

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject

/** The subset of android.content.SharedPreferences used by SABI-FOX. */
interface SharedPreferences {
    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }

    val all: Map<String, *>

    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    operator fun contains(key: String): Boolean
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
}

/**
 * Preferences persisted as a small typed JSON document. Writes are atomic (temp file + move)
 * so a crash or power loss never leaves a half-written settings file.
 */
internal class JsonSharedPreferences private constructor(private val file: File) : SharedPreferences {
    private val lock = Any()
    private val values: MutableMap<String, Any> = load()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    override val all: Map<String, *>
        get() = synchronized(lock) { values.toMap() }

    override fun getString(key: String, defValue: String?): String? =
        synchronized(lock) { values[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        synchronized(lock) { (values[key] as? Set<String>)?.toSet() ?: defValues }

    override fun getInt(key: String, defValue: Int): Int =
        synchronized(lock) { (values[key] as? Number)?.toInt() ?: defValue }

    override fun getLong(key: String, defValue: Long): Long =
        synchronized(lock) { (values[key] as? Number)?.toLong() ?: defValue }

    override fun getFloat(key: String, defValue: Float): Float =
        synchronized(lock) { (values[key] as? Number)?.toFloat() ?: defValue }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        synchronized(lock) { values[key] as? Boolean ?: defValue }

    override fun contains(key: String): Boolean = synchronized(lock) { key in values }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.addIfAbsent(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clearAll = false

        private fun put(key: String, value: Any?) = apply { synchronized(changes) { changes[key] = value } }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.toSet())
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            val changedKeys = mutableListOf<String?>()
            val ok =
                synchronized(lock) {
                    if (clearAll && values.isNotEmpty()) {
                        values.clear()
                        changedKeys.add(null)
                    }
                    val pending = synchronized(changes) { changes.toMap() }
                    for ((key, value) in pending) {
                        val previous = values[key]
                        if (value == null) values.remove(key) else values[key] = value
                        if (previous != value) changedKeys.add(key)
                    }
                    runCatching { persist() }.isSuccess
                }
            for (key in changedKeys) {
                for (listener in listeners) {
                    runCatching { listener.onSharedPreferenceChanged(this@JsonSharedPreferences, key) }
                }
            }
            return ok
        }

        override fun apply() {
            commit()
        }
    }

    private fun load(): MutableMap<String, Any> {
        val result = ConcurrentHashMap<String, Any>()
        if (!file.isFile) return result
        runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            for (key in root.keys()) {
                val entry = root.optJSONObject(key) ?: continue
                val value: Any? =
                    when (entry.optString("t")) {
                        "s" -> entry.optString("v")
                        "i" -> entry.optInt("v")
                        "l" -> entry.optLong("v")
                        "f" -> entry.optDouble("v").toFloat()
                        "b" -> entry.optBoolean("v")
                        "ss" -> entry.optJSONArray("v")?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
                        else -> null
                    }
                if (value != null) result[key] = value
            }
        }
        return result
    }

    private fun persist() {
        val root = JSONObject()
        for ((key, value) in values) {
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is Boolean -> entry.put("t", "b").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            root.put(key, entry)
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(root.toString(), Charsets.UTF_8)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, JsonSharedPreferences>()

        fun open(file: File): SharedPreferences =
            instances.computeIfAbsent(file.absolutePath) { JsonSharedPreferences(file) }
    }
}

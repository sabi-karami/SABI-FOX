package com.sabifox.vpn.data

import com.sabifox.vpn.platform.Context
import com.sabifox.vpn.platform.SharedPreferences

/** Windows never had the legacy Android credential format, so there is nothing to migrate. */
internal object LegacyCredentialReader {
    fun open(context: Context, name: String): SharedPreferences =
        context.getSharedPreferences("${name}_legacy_unused", Context.MODE_PRIVATE)
}

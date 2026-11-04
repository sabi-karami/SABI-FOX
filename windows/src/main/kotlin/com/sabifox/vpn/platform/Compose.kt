package com.sabifox.vpn.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString

/** The application context, provided at the root of the window. */
val LocalContext = staticCompositionLocalOf<Context> { error("No SABI-FOX context provided") }

/** Desktop has no system back gesture; Esc handling lives at window level. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}

/** Non-composable string lookup, matching Context.getString on Android. */
fun Context.getString(resource: StringResource, vararg args: Any): String = runBlocking {
    org.jetbrains.compose.resources.getString(resource, *args)
}

fun Context.getQuantityString(resource: PluralStringResource, quantity: Int, vararg args: Any): String =
    runBlocking { getPluralString(resource, quantity, *args) }

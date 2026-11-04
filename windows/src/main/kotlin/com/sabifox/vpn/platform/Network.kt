package com.sabifox.vpn.platform

import java.net.InetAddress
import java.net.Socket

/**
 * Placeholder for android.net.Network. On Windows the core engine keeps its own traffic off the
 * tunnel, and SABI-FOX's control-plane requests are routed directly by a process rule, so there
 * is nothing to bind.
 */
class Network {
    fun bindSocket(@Suppress("UNUSED_PARAMETER") socket: Socket) {}

    fun getAllByName(host: String): Array<InetAddress> = InetAddress.getAllByName(host)
}

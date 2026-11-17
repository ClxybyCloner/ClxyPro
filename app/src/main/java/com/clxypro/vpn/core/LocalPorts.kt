package com.clxypro.vpn.core

import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * The port an engine binds its local SOCKS5 proxy to. The preferred port
 * (1080) can already be held — usually by an engine child orphaned when the
 * app was killed without a disconnect, sometimes by another app on the
 * device. Binding the same busy port again fails every retry identically,
 * so resolve a port that is actually free: the preferred one when it is
 * free, otherwise an ephemeral one. The callers that need the core's port
 * (bridge config, watchdog, reachability) all read it back from the core,
 * and ProxyFront keeps serving the preferred port by relaying to it.
 */
object LocalPorts {
    fun resolve(preferred: Int, log: (String) -> Unit): Int {
        if (isFree(preferred)) return preferred
        log("SOCKS port $preferred is busy, picking a free port")
        return runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(preferred)
    }

    private fun isFree(port: Int): Boolean = runCatching {
        ServerSocket().apply {
            // Mirrors the engines' own bind: TIME_WAIT alone must not count
            // as busy.
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", port))
        }.close()
        true
    }.getOrDefault(false)
}

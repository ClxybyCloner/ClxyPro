package com.clxypro.vpn.core

import android.content.Context
import android.os.PowerManager

/**
 * Keeps the CPU awake while a tunnel session exists.
 *
 * A foreground service keeps the process alive, but it does NOT keep the CPU
 * awake: once the screen is off and Doze sets in, the CPU sleeps and everything
 * that keeps the tunnel alive freezes — the watchdog's probe loop, the retry
 * timer (`Handler.postDelayed`), and the engine's own network threads. The
 * MASQUE session then goes stale (no keepalives, dead UDP path) and the tunnel
 * drops; the queued retry never fires until the user wakes the phone, and the
 * connect attempt that follows can sit on "Connecting..." for its whole
 * 15-minute cap. That is the screen-off drop.
 *
 * The lock is a partial wake lock, held from the first connect attempt until
 * the session ends (teardown), so it covers connecting, the retry waits and
 * the connected state — and nothing else. It is never held while disconnected,
 * and it dies with the process if the system kills the app. The VPN service
 * and the proxy service share one process-wide lock, refcounted by owner, so
 * a mode switch cannot drop the other service's lock.
 */
object ServiceWakeLock {
    private val holders = mutableSetOf<String>()
    private var lock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context, owner: String) {
        if (!holders.add(owner)) return
        if (lock?.isHeld == true) return
        val pm = context.getSystemService(PowerManager::class.java) ?: run {
            holders.remove(owner)
            return
        }
        lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClxyPro:Connection").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    @Synchronized
    fun release(owner: String) {
        if (!holders.remove(owner)) return
        if (holders.isNotEmpty()) return
        runCatching { if (lock?.isHeld == true) lock?.release() }
        lock = null
    }
}

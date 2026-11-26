package com.clxypro.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clxypro.vpn.core.AppState

/**
 * The Disconnect button on the tunnel notification. The notification itself
 * does not know whether the VPN or the proxy service is running, so the
 * decision is made here at tap time from the current mode. Both stop()
 * calls are safe from any state: they set wantsClosed first, so a retry
 * queued for later never resurrects the tunnel after the user tapped
 * Disconnect.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DISCONNECT) return
        if (AppState.state.mode == AppState.Mode.PROXY) {
            ProxyService.stop(context)
        } else {
            TunnelVpnService.stop(context)
        }
    }

    companion object {
        const val ACTION_DISCONNECT = "com.clxypro.vpn.notification.DISCONNECT"
    }
}

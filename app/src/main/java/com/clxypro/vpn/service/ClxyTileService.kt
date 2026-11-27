package com.clxypro.vpn.service

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.clxypro.vpn.MainActivity
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.vpn.Store

/**
 * Quick Settings tile: one tap connects or disconnects, the same as the
 * power button on Home. Anything that needs the UI — sign-in, the Quantum
 * serial, or the system VPN consent — opens the app and lets it run its
 * normal toggle flow instead of failing silently from the tile.
 */
class ClxyTileService : TileService() {
    private var stopListening: (() -> Unit)? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        stopListening?.invoke()
        stopListening = AppState.listen { updateTile() }
    }

    override fun onStopListening() {
        stopListening?.invoke()
        stopListening = null
        super.onStopListening()
    }

    @Suppress("DEPRECATION") // startActivityAndCollapse(Intent) is deprecated
    // on API 34+ in favour of the PendingIntent variant, but the Intent
    // variant is the one that works back to the min SDK.
    override fun onClick() {
        super.onClick()
        val state = AppState.state
        if (state.disconnecting) return
        if (state.connected || state.running || state.busy) {
            if (state.mode == AppState.Mode.PROXY) ProxyService.stop(this)
            else TunnelVpnService.stop(this)
            return
        }
        if (needsUi(state)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_AUTO_CONNECT, true)
            }
            startActivityAndCollapse(intent)
            return
        }
        if (Prefs.mode == AppState.Mode.PROXY) ProxyService.start(this, state.coreId)
        else TunnelVpnService.start(this, state.coreId)
    }

    private fun needsUi(state: AppState.State): Boolean {
        if (state.coreId == "firefox" && !Store.hasSession(this)) return true
        // Both engines need the same serial on first connect; the tile
        // routes through MainActivity so the dialog can ask for it.
        if (!Prefs.quantumUnlocked) return true
        // Proxy mode never touches the system VPN, so it needs no consent.
        if (Prefs.mode != AppState.Mode.PROXY && VpnService.prepare(this) != null) return true
        return false
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val state = AppState.state
        tile.state = if (state.connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                state.connected -> state.exit.ifBlank { "Connected" }
                state.running || state.busy -> "Connecting…"
                else -> "Disconnected"
            }
        }
        tile.updateTile()
    }
}

package com.clxypro.vpn

import android.app.Application
import com.clxypro.vpn.core.AetherCore
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.CaBundle
import com.clxypro.vpn.core.CoreRegistry
import com.clxypro.vpn.core.CrashGuard
import com.clxypro.vpn.core.FirefoxCore
import com.clxypro.vpn.core.GithubUpdates
import com.clxypro.vpn.core.LogRetention
import com.clxypro.vpn.core.NetworkMonitor
import com.clxypro.vpn.core.OriginCountry
import com.clxypro.vpn.core.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // A worker that dies on a dropped connection must not take the tunnel,
        // the notification or the process with it.
        CrashGuard.install()
        // Before any engine opens a log file for this run.
        LogRetention.sweep(filesDir)
        // Before the first TLS handshake: the device's root set is frozen at the
        // image build date, and the engines are handed this bundle instead.
        CaBundle.prepare(this)
        Prefs.init(this)
        // 2026-11-21: the "Fastest" pseudo-server is gone (its latency pick
        // landed on dead edges); a stored "fastest" pin becomes Auto, which
        // now dials Germany first.
        if (Prefs.server("firefox") == "fastest") Prefs.setServer("firefox", "", "")
        // Firefox (CLXY Hyper) stays the default engine; Aether (CLXY Quantum) follows.
        CoreRegistry.register(FirefoxCore())
        CoreRegistry.register(AetherCore())
        CoreRegistry.all().forEach { core ->
            runCatching { core.init(this) }
                .onFailure { AppState.log("core ${core.id} init failed: ${it.message ?: it.javaClass.simpleName}") }
        }
        restoreSettings()
        NetworkMonitor.start(this)
        // The "Your Device" flag comes from the public IP, read once here
        // before any tunnel is up — never from the system locale.
        OriginCountry.detect()
        GithubUpdates.autoCheck()
    }

    private fun restoreSettings() {
        val core = CoreRegistry.get(Prefs.coreId.ifBlank { null })
        if (Prefs.coreId != core.id) {
            Prefs.coreId = core.id
        }
        AppState.update {
            it.copy(
                mode = Prefs.mode,
                coreId = core.id,
                shareLan = Prefs.shareLan
            )
        }
    }
}

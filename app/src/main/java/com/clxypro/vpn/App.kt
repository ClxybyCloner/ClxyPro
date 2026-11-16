package com.clxypro.vpn

import android.app.Application
import com.clxypro.vpn.core.AetherCore
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.CaBundle
import com.clxypro.vpn.core.CoreRegistry
import com.clxypro.vpn.core.CrashGuard
import com.clxypro.vpn.core.FirefoxCore
import com.clxypro.vpn.core.GithubUpdates
import com.clxypro.vpn.core.LanternCore
import com.clxypro.vpn.core.LogRetention
import com.clxypro.vpn.core.NetworkMonitor
import com.clxypro.vpn.core.OpenRungCore
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.PsiphonCore
import com.clxypro.vpn.core.SstpCore
import com.clxypro.vpn.core.SunAndLionCore
import com.clxypro.vpn.core.TorCore

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
        // Firefox stays the default engine; the ported cores follow it in the picker.
        CoreRegistry.register(FirefoxCore())
        CoreRegistry.register(AetherCore())
        CoreRegistry.register(PsiphonCore())
        CoreRegistry.register(SunAndLionCore())
        CoreRegistry.register(SstpCore())
        CoreRegistry.register(LanternCore())
        CoreRegistry.register(OpenRungCore())
        CoreRegistry.register(TorCore())
        CoreRegistry.all().forEach { core ->
            runCatching { core.init(this) }
                .onFailure { AppState.log("core ${core.id} init failed: ${it.message ?: it.javaClass.simpleName}") }
        }
        restoreSettings()
        NetworkMonitor.start(this)
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

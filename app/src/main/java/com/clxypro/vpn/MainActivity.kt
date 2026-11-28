package com.clxypro.vpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.clxypro.vpn.Texts
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.GithubUpdates
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.QuantumSerial
import com.clxypro.vpn.core.SerialCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.clxypro.vpn.service.ProxyService
import com.clxypro.vpn.service.TunnelVpnService
import com.clxypro.vpn.ui.ClxyBottomNavBar
import com.clxypro.vpn.ui.ClxyCosmicBackground
import com.clxypro.vpn.ui.HomeScreen
import com.clxypro.vpn.ui.ClxyTheme
import com.clxypro.vpn.ui.MainTab
import com.clxypro.vpn.ui.DiagnosticsLogsDialog
import com.clxypro.vpn.ui.ProfileScreen
import com.clxypro.vpn.ui.QuantumSerialDialog
import com.clxypro.vpn.ui.ServersScreen
import com.clxypro.vpn.vpn.Store

class MainActivity : ComponentActivity() {
    companion object {
        /** Set by the Quick Settings tile: run the normal toggle once the UI is up. */
        const val EXTRA_AUTO_CONNECT = "clxy_auto_connect"
    }

    private var pendingConnect = false

    /** Serial prompt; raised by toggle() on first connect of either engine. */
    private val serialDialogOpen = mutableStateOf(false)

    /** Which engine the open serial dialog is for: Hyper asks separately
     *  from Quantum, even though both take the same serials. */
    private val serialForHyper = mutableStateOf(false)

    /** Why the serial dialog reopened after an unlock (expired / locked
     *  serial) — shown inside the dialog so the user knows what happened. */
    private val serialNotice = mutableStateOf<String?>(null)

    /** Bumped whenever an auto-connect request arrives (tile tap). */
    private val autoConnectTick = mutableStateOf(0)

    /** The system VPN consent dialog; the tunnel only starts on RESULT_OK. */
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val granted = result.resultCode == RESULT_OK
        val state = AppState.state
        if (granted && pendingConnect && !state.disconnecting && !state.running && !state.busy) {
            TunnelVpnService.start(this, state.coreId)
        }
        pendingConnect = false
    }

    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val webSignIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val token = data.getStringExtra("token").orEmpty()
            val email = data.getStringExtra("email").orEmpty().ifBlank { Store.email(this).orEmpty() }
            if (token.length >= 64) {
                Store.setEmail(this, email)
                runCatching { Store.sessionFile(this).writeText(token) }
                AppState.log("engine firefox sign-in confirmed for $email")
                toggle { }
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        AppLocale.sync(this)
        // Every app open looks for a newer release (throttled inside).
        GithubUpdates.maybeCheck(this)
        // The home screen shows the serial's remaining days; refresh it
        // after midnight so the number is never a day stale.
        refreshSerialDays()
    }

    /** Refreshes the displayed remaining days after midnight (once a day,
     *  in the background, never blocking a connect). A Locked verdict is
     *  left for toggle() to handle at the next connect. */
    private fun refreshSerialDays() {
        val hash = Prefs.serialHash
        if (hash.isEmpty()) return
        // If we already have today's number, nothing to do. But if the
        // number was never stored — e.g. the day was already checked by
        // an older version that did not save it — fetch it even though
        // the day was checked, or the home pill has nothing to show.
        if (Prefs.serialCheckDay == todayDay() && Prefs.serialDaysLeft >= 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = SerialCheck.check(hash)) {
                is SerialCheck.Result.Active -> {
                    Prefs.serialCheckDay = todayDay()
                    Prefs.serialDaysLeft = result.daysLeft
                    AppState.update { it.copy(serialDaysLeft = result.daysLeft) }
                }
                SerialCheck.Result.Unknown -> Prefs.serialCheckDay = todayDay()
                is SerialCheck.Result.Locked -> Unit
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTO_CONNECT, false)) {
            autoConnectTick.value += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore the cached remaining-days number so the home pill shows
        // it before the first check of the day runs.
        AppState.update { it.copy(serialDaysLeft = Prefs.serialDaysLeft) }
        if (intent.getBooleanExtra(EXTRA_AUTO_CONNECT, false)) {
            autoConnectTick.value = 1
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Says which way the screen is laid out, so a language that did not take
        // effect shows up in the log instead of only on the screen.
        AppState.log(
            "ui lang=${AppLocale.current(this)}" +
                " dir=${if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "rtl" else "ltr"}"
        )

        setContent {
            ClxyTheme {
                var state by remember { mutableStateOf(AppState.state) }
                var activeTab by remember { mutableStateOf(MainTab.HOME) }
                var logsOpen by remember { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    val stop = AppState.listen { state = it }
                    onDispose { stop() }
                }

                // Quick Settings tile tap: run the same toggle the power
                // button runs (sign-in / serial / VPN consent included),
                // but only when actually disconnected.
                androidx.compose.runtime.LaunchedEffect(autoConnectTick.value) {
                    if (autoConnectTick.value > 0) {
                        val current = AppState.state
                        if (!current.connected && !current.running && !current.busy && !current.disconnecting) {
                            toggle { activeTab = MainTab.PROFILE }
                        }
                    }
                }

                if (activeTab != MainTab.HOME) {
                    BackHandler { activeTab = MainTab.HOME }
                }

                ClxyCosmicBackground {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.statusBars,
                        bottomBar = {
                            ClxyBottomNavBar(
                                activeTab = activeTab,
                                onSelectTab = { activeTab = it },
                                isQuantum = state.coreId == "aether",
                                onOpenLogs = { logsOpen = true },
                                logsOpen = logsOpen,
                            )
                        },
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            if (serialDialogOpen.value) {
                                QuantumSerialDialog(
                                    onDismiss = {
                                        serialDialogOpen.value = false
                                        serialNotice.value = null
                                    },
                                    deviceId = android.provider.Settings.Secure.getString(
                                        contentResolver,
                                        android.provider.Settings.Secure.ANDROID_ID,
                                    ) ?: "",
                                    onValid = { code ->
                                        if (serialForHyper.value) Prefs.hyperUnlocked = true
                                        else Prefs.quantumUnlocked = true
                                        // Only the hash is kept — the serial itself is never stored.
                                        Prefs.serialHash = QuantumSerial.hashOf(code)
                                    },
                                    onActivated = {
                                        serialDialogOpen.value = false
                                        serialNotice.value = null
                                        toggle { activeTab = MainTab.PROFILE }
                                    },
                                    notice = serialNotice.value,
                                )
                            }
                            if (logsOpen) {
                                DiagnosticsLogsDialog(state = state, onDismiss = { logsOpen = false })
                            } else {
                                AnimatedContent(
                                    targetState = activeTab,
                                    transitionSpec = {
                                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                        (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 12 * dir }) togetherWith
                                            (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 12 * dir })
                                    },
                                    label = "tabTransition",
                                ) { tab ->
                                    when (tab) {
                                        MainTab.HOME -> HomeScreen(
                                            state = state,
                                            onToggle = { toggle { activeTab = MainTab.PROFILE } },
                                            onOpenSettings = { activeTab = MainTab.PROFILE },
                                            onOpenServers = { activeTab = MainTab.SERVERS },
                                            onOpenSignIn = {
                                                webSignIn.launch(Intent(this@MainActivity, SignInActivity::class.java))
                                            },
                                            onOpenLogs = { logsOpen = true },
                                        )
                                        MainTab.SERVERS -> ServersScreen(
                                            state = state,
                                            onSelectServer = { key, label ->
                                                Prefs.setServer(state.coreId, key, label)
                                                if (!state.connected && !state.running && !state.busy) {
                                                    toggle { activeTab = MainTab.PROFILE }
                                                }
                                                activeTab = MainTab.HOME
                                            },
                                            onCloseToHome = { activeTab = MainTab.HOME },
                                        )
                                        MainTab.PROFILE -> ProfileScreen(
                                            state = state,
                                            onNavigateToServers = { activeTab = MainTab.SERVERS },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggle(openSettings: () -> Unit) {
        val state = AppState.state
        if (state.disconnecting) return
        if (state.connected || state.running || state.busy) {
            stopSession()
            return
        }
        val coreId = state.coreId
        // The CLXY Hyper (Firefox) core rides on the user's account; launch in-app sign-in directly.
        if (coreId == "firefox" && !Store.hasSession(this)) {
            webSignIn.launch(Intent(this, SignInActivity::class.java))
            return
        }
        // Each engine asks for the serial once, on its own first connect.
        // Both take the same serials, but a Quantum unlock does not unlock
        // Hyper (and vice versa) — so Hyper always asks, even on a device
        // that already unlocked Quantum.
        // A device unlocked before v1.5.35 has no stored serial hash, so
        // it asks once more, on its next connect, to capture it — after
        // that, the online check below takes over.
        if (coreId == "firefox" && (!Prefs.hyperUnlocked || Prefs.serialHash.isEmpty())) {
            serialForHyper.value = true
            serialDialogOpen.value = true
            return
        }
        if (coreId == "aether" && (!Prefs.quantumUnlocked || Prefs.serialHash.isEmpty())) {
            serialForHyper.value = false
            serialDialogOpen.value = true
            return
        }
        // Already unlocked: the panel check runs at most once a day — on
        // the first connect after midnight — so it never slows connects
        // down. Every other connect today goes straight through.
        val hash = Prefs.serialHash
        if (hash.isEmpty()) {
            startSession(coreId)
            return
        }
        if (Prefs.serialCheckDay == todayDay()) {
            startSession(coreId)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = SerialCheck.check(hash)) {
                is SerialCheck.Result.Active -> {
                    AppState.log("serial: active, ${result.daysLeft} days left")
                    Prefs.serialCheckDay = todayDay()
                    Prefs.serialDaysLeft = result.daysLeft
                    AppState.update { it.copy(serialDaysLeft = result.daysLeft) }
                    withContext(Dispatchers.Main) { startSession(coreId) }
                }
                is SerialCheck.Result.Locked -> {
                    AppState.log("serial: ${result.reason}, unlock cleared")
                    Prefs.hyperUnlocked = false
                    Prefs.quantumUnlocked = false
                    Prefs.serialHash = ""
                    withContext(Dispatchers.Main) {
                        serialNotice.value =
                            if (result.reason == "expired") Texts.serialExpired
                            else Texts.serialRevoked
                        serialForHyper.value = coreId == "firefox"
                        serialDialogOpen.value = true
                    }
                }
                SerialCheck.Result.Unknown -> {
                    // No verdict today (offline / rate-limited / unknown):
                    // count the day as checked, so the next connects today
                    // are instant, and try again tomorrow.
                    Prefs.serialCheckDay = todayDay()
                    withContext(Dispatchers.Main) { startSession(coreId) }
                }
            }
        }
    }

    /** Local calendar day, for the once-a-day serial check. */
    private fun todayDay(): Long {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 1000L +
            cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun startSession(coreId: String) {
        askNotifications()
        if (Prefs.mode == AppState.Mode.PROXY) {
            ProxyService.start(this, coreId)
            return
        }
        // Proxy mode never touches the system VPN, so it needs no consent.
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingConnect = true
            consent.launch(intent)
        } else {
            TunnelVpnService.start(this, coreId)
        }
    }

    private fun stopSession() {
        val state = AppState.state
        if (state.disconnecting) return
        if (state.mode == AppState.Mode.PROXY) {
            ProxyService.stop(this)
        } else {
            TunnelVpnService.stop(this)
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

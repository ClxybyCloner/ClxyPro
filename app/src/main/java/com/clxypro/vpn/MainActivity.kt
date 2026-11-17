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
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.Prefs
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
    private var pendingConnect = false

    /** Quantum serial prompt; raised by toggle() on first Quantum connect. */
    private val serialDialogOpen = mutableStateOf(false)

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                androidx.compose.runtime.LaunchedEffect(state.coreId) {
                    if (state.coreId == "aether") {
                        activeTab = MainTab.HOME
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
                                    onDismiss = { serialDialogOpen.value = false },
                                    onActivated = {
                                        serialDialogOpen.value = false
                                        toggle { activeTab = MainTab.PROFILE }
                                    },
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
        // Quantum (Aether) needs a serial on first connect, the way Hyper
        // needs a sign-in. Asked once; the unlock is remembered.
        if (coreId == "aether" && !Prefs.quantumUnlocked) {
            serialDialogOpen.value = true
            return
        }
        // The CLXY Hyper (Firefox) core rides on the user's account; launch in-app sign-in directly.
        if (coreId == "firefox" && !Store.hasSession(this)) {
            webSignIn.launch(Intent(this, SignInActivity::class.java))
            return
        }
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

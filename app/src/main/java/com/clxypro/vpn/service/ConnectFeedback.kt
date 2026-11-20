package com.clxypro.vpn.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.Prefs

/**
 * Two short pulses when the tunnel comes up, so a connection that finishes
 * while the phone is face-down is still noticed — without a sound. (There
 * used to be a chime here; it sounded even with the ringer muted.)
 */
object ConnectFeedback {
    private val PATTERN = longArrayOf(0, 70, 80, 140)

    fun signal(context: Context) {
        if (!Prefs.connectVibration) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
            AppState.log("connect vibration started")
        }.onFailure {
            AppState.log("connect vibration failed: ${it.message ?: it.javaClass.simpleName}")
        }
    }
}

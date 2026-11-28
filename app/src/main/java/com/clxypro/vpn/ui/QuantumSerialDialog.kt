package com.clxypro.vpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.clxypro.vpn.core.SerialCheck
import com.clxypro.vpn.ui.theme.ClxyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-connect serial prompt, shared by Hyper and Quantum: both take the
 * same serials, but each engine asks once on its own first connect. A valid
 * serial unlocks the engine that asked (via [onValid]); an invalid one just
 * shows an error and stays on the dialog.
 */
@Composable
fun QuantumSerialDialog(
    onDismiss: () -> Unit,
    onValid: (String) -> Unit,
    onActivated: () -> Unit,
    /** Shown when the dialog reopened because the panel killed the old
     *  serial (expired / locked) — so the user knows why they are asked again. */
    notice: String? = null,
    /** Stable per-device id (ANDROID_ID), sent to the panel at activation
     *  so it can count distinct devices against the serial's max uses. */
    deviceId: String = "",
) {
    var code by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var limitReached by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (checking || success) return
        // One activation path for every serial: built-in ones are
        // accepted offline, panel serials must be active on the panel,
        // and either way this device is registered against the serial's
        // max uses (see SerialCheck.activate).
        val submitted = code
        checking = true
        invalid = false
        limitReached = false
        scope.launch(Dispatchers.IO) {
            val result = SerialCheck.activate(submitted, deviceId)
            withContext(Dispatchers.Main) {
                checking = false
                when (result) {
                    SerialCheck.Activation.Valid -> {
                        // Show the success celebration, then close and connect.
                        success = true
                        scope.launch {
                            delay(900)
                            onValid(submitted)
                            onActivated()
                        }
                    }
                    SerialCheck.Activation.Invalid -> invalid = true
                    is SerialCheck.Activation.LimitReached -> limitReached = true
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!checking && !success) onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ClxyColors.CardSurface)
                .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.VpnKey,
                contentDescription = null,
                tint = ClxyColors.CyanText,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = "CLXY Serial",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = ClxyColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Enter your serial number to activate CLXY Hyper and Quantum on this device.",
                fontSize = 12.sp,
                color = ClxyColors.TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
            OutlinedTextField(
                value = code,
                onValueChange = {
                    // 12 letters/digits plus up to 2 dashes (XXXX-XXXX-XXXX).
                    code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(14)
                    invalid = false
                },
                placeholder = { Text("XXXX-XXXX-XXXX") },
                singleLine = true,
                isError = invalid,
                colors = clxyTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (notice != null) {
                Text(
                    text = notice,
                    fontSize = 11.5.sp,
                    color = ClxyColors.RedDisconnectStart,
                    textAlign = TextAlign.Center,
                )
            }
            if (invalid) {
                Text(
                    text = "Invalid serial number. Please check it and try again.",
                    fontSize = 11.5.sp,
                    color = ClxyColors.RedDisconnectStart,
                    textAlign = TextAlign.Center,
                )
            }
            if (limitReached) {
                Text(
                    text = "This serial has reached its usage limit. Ask the owner for a new serial.",
                    fontSize = 11.5.sp,
                    color = ClxyColors.RedDisconnectStart,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = { if (!checking && !success) onDismiss() },
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text("Cancel", color = ClxyColors.TextMuted, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { submit() },
                    enabled = code.isNotBlank() && !checking && !success,
                    colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text("Activate", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
            if (success) {
                ActivationSuccessView(
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

/** Full-card success celebration shown briefly after a serial activates:
 *  a green check that springs in inside a glowing ring. */
@Composable
private fun ActivationSuccessView(
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    Box(
        modifier = modifier
            .background(ClxyColors.CardSurface)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(scale.value)
                    .alpha(scale.value)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                    .border(2.dp, Color(0xFF22C55E), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                text = "Serial activated",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = ClxyColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Welcome to CLXY.",
                fontSize = 12.sp,
                color = ClxyColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

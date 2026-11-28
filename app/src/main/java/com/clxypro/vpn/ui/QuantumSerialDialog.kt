package com.clxypro.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.clxypro.vpn.core.QuantumSerial
import com.clxypro.vpn.ui.theme.ClxyColors

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
) {
    var code by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    fun submit() {
        if (QuantumSerial.isValid(code)) {
            onValid(code)
            onActivated()
        } else {
            invalid = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ClxyColors.CardSurface)
                .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
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
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text("Cancel", color = ClxyColors.TextMuted, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { submit() },
                    enabled = code.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text("Activate", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

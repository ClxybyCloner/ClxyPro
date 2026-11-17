package com.clxypro.vpn

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.clxypro.vpn.ui.ClxyCosmicBackground
import com.clxypro.vpn.ui.ClxyTheme
import com.clxypro.vpn.ui.clxyTextFieldColors
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.vpn.FxaLogin
import com.clxypro.vpn.vpn.LoginPrompt
import com.clxypro.vpn.vpn.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 100% In-App Native CLXY Cloud Sign-in.
 * Handles credentials, CAPTCHA, TOTP, and unblock challenges directly inside the app,
 * never opening external browsers or third-party webviews.
 */
class SignInActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        AppLocale.sync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ClxyTheme {
                ClxyCosmicBackground {
                    SignInScreen()
                }
            }
        }
    }

    internal fun deliver(token: String, email: String) {
        setResult(RESULT_OK, Intent().putExtra("token", token).putExtra("email", email))
        finish()
    }
}

@Composable
private fun SignInScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? SignInActivity

    var email by remember { mutableStateOf(Store.email(context).orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var unblock by remember { mutableStateOf("") }
    var showUnblock by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf<LoginPrompt?>(null) }
    var captcha by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf<FxaLogin?>(null) }

    DisposableEffect(Unit) {
        onDispose { login?.cancel() }
    }

    fun start() {
        if (email.isBlank() || password.isBlank()) {
            error = context.getString(R.string.sign_in_missing)
            return
        }
        val address = email.trim()
        val driver = FxaLogin(Store.workDir(context))
        login = driver
        error = null
        detail = ""
        code = ""
        captcha = ""
        prompt = null
        busy = true
        status = context.getString(R.string.sign_in_working)

        scope.launch {
            val problem = withContext(Dispatchers.IO) {
                driver.signIn(
                    email = address,
                    password = password,
                    unblockCode = unblock.trim(),
                    onPrompt = { prompt = it },
                    onCaptcha = { captcha = it },
                    onStatus = { line ->
                        status = if (line.contains("confirmation email", ignoreCase = true)) {
                            context.getString(R.string.sign_in_confirm)
                        } else {
                            line.replace("Mozilla", "CLXY", ignoreCase = true)
                                .replace("Firefox", "CLXY", ignoreCase = true)
                        }
                    }
                )
            }
            busy = false
            login = null
            if (problem == null) {
                val token = runCatching { Store.sessionFile(context).readText().trim() }
                    .getOrDefault("")
                if (token.length >= TOKEN_LENGTH) {
                    Store.setEmail(context, address)
                    activity?.deliver(token, address)
                    return@launch
                }
            }
            prompt = null
            val failure = describeFailure(context, problem)
            error = failure.first
            detail = failure.second
            if (problem.orEmpty().contains("errno 125") || problem.orEmpty().contains("authorization", ignoreCase = true)) {
                showUnblock = true
            }
        }
    }

    fun cancel() {
        login?.cancel()
        showUnblock = false
        activity?.finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ClxyColors.ObsidianCard.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, ClxyColors.GlassBorder),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Icon & Title
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClxyColors.CyanGlow.copy(alpha = 0.16f))
                        .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = ClxyColors.CyanText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "CLXY Cloud Sign-in",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClxyColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Sign in with your CLXY Cloud credentials to activate unlimited high-speed MASQUE routing.",
                    fontSize = 12.sp,
                    color = ClxyColors.TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                val asking = prompt
                if (asking != null) {
                    // Prompt Handling (CAPTCHA or 2FA)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = when (asking) {
                                LoginPrompt.Totp -> "Two-Factor Authentication"
                                LoginPrompt.EmailCode -> "Email Verification Code"
                                LoginPrompt.Captcha -> "Security Verification"
                                LoginPrompt.UnblockCode -> "Account Unblock Code"
                                else -> "Verification Required"
                            },
                            color = ClxyColors.CyanText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Text(
                            text = when (asking) {
                                LoginPrompt.Totp -> "Enter the 6-digit authentication code from your app."
                                LoginPrompt.EmailCode -> "Enter the 6-digit confirmation code sent to your email."
                                LoginPrompt.Captcha -> "Type the characters shown in the picture below."
                                LoginPrompt.UnblockCode -> "Enter the security unblock code sent to your email."
                                else -> "Enter the requested verification code."
                            },
                            color = ClxyColors.TextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )

                        if (asking == LoginPrompt.Captcha) {
                            CaptchaImage(dataUrl = captcha)
                        }

                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            placeholder = { Text("Enter code") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (asking == LoginPrompt.Totp || asking == LoginPrompt.EmailCode) KeyboardType.Number else KeyboardType.Text
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                login?.answer(code)
                                code = ""
                                captcha = ""
                                prompt = null
                                error = null
                                detail = ""
                                status = context.getString(R.string.sign_in_working)
                            },
                            enabled = code.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("Submit Code", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (showUnblock) {
                    // Unblock Code Challenge
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, ClxyColors.AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "CLXY Cloud Authorization Code",
                            color = ClxyColors.AmberWarning,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "CLXY Cloud emailed an authorization code to verify your connection. Enter it below.",
                            color = ClxyColors.TextMuted,
                            fontSize = 11.5.sp
                        )
                        OutlinedTextField(
                            value = unblock,
                            onValueChange = { unblock = it },
                            placeholder = { Text("Authorization code") },
                            singleLine = true,
                            enabled = !busy,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { start() },
                            enabled = !busy && unblock.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("Confirm & Sign In", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { showUnblock = false; unblock = "" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel Code", color = ClxyColors.TextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    // Standard In-App Login Form
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Email or Username") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Mail, contentDescription = null, tint = ClxyColors.TextMuted, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = clxyTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = ClxyColors.TextMuted, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }, enabled = !busy) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = ClxyColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = clxyTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Button(
                        onClick = { start() },
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CyanGlow.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.size(10.dp))
                            Text("Authenticating…", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Sign In to CLXY Cloud", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = { showUnblock = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Have an authorization code?", color = ClxyColors.CyanText, fontSize = 12.sp)
                    }
                }

                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        fontSize = 11.5.sp,
                        color = ClxyColors.CyanText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = ClxyColors.RedDisconnectStart,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        fontSize = 10.5.sp,
                        color = ClxyColors.TextDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TextButton(
                    onClick = { cancel() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = ClxyColors.TextMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CaptchaImage(dataUrl: String) {
    val bitmap = remember(dataUrl) { decodeDataUrl(dataUrl) }
    if (bitmap == null) {
        Text(
            text = "CAPTCHA image could not be loaded.",
            fontSize = 11.sp,
            color = ClxyColors.RedDisconnectStart,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "CAPTCHA",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(6.dp)
    )
}

private fun decodeDataUrl(dataUrl: String): android.graphics.Bitmap? {
    val payload = dataUrl.substringAfter("base64,", "")
    if (payload.isEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun describeFailure(context: Context, problem: String?): Pair<String, String> {
    val raw = problem.orEmpty().trim()
        .replace("Mozilla", "CLXY", ignoreCase = true)
        .replace("Firefox", "CLXY", ignoreCase = true)

    val friendly = when {
        raw.contains("CAPTCHA", ignoreCase = true) ||
            raw.contains("edge protection", ignoreCase = true) ||
            raw.contains("HTTP 406", ignoreCase = true) -> R.string.sign_in_err_blocked
        raw.contains("errno 103") || raw.contains("Incorrect password", ignoreCase = true) ->
            R.string.sign_in_err_password
        raw.contains("errno 125") -> R.string.sign_in_err_unblock
        raw.contains("errno 201") -> R.string.sign_in_err_unverified
        raw.contains("errno 138") -> R.string.sign_in_err_unconfirmed
        raw.contains("errno 102") -> R.string.sign_in_err_unknown
        raw.contains("timed out", ignoreCase = true) -> R.string.sign_in_err_timeout
        raw.contains("request to", ignoreCase = true) ||
            raw.contains("handshake", ignoreCase = true) ||
            raw.contains("HTTP 5", ignoreCase = true) -> R.string.sign_in_err_network
        else -> R.string.sign_in_failed
    }
    val message = context.getString(friendly)
    return message to if (raw.isNotBlank() && raw != message) raw.take(180) else ""
}

private const val TOKEN_LENGTH = 64

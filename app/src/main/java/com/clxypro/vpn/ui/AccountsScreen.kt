package com.clxypro.vpn.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.vpn.SavedAccount
import com.clxypro.vpn.vpn.Store

@Composable
fun AccountsScreen(
    state: AppState.State,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf(Store.accounts(context)) }
    var activeAccount by remember { mutableStateOf(Store.activeAccount(context)) }
    var threshold by remember { mutableIntStateOf(Store.autoRotateThreshold(context)) }
    var showManualTokenDialog by remember { mutableStateOf(false) }

    fun refresh() {
        accounts = Store.accounts(context)
        activeAccount = Store.activeAccount(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClxyColors.ObsidianBg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "اکانت‌های ابری",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClxyColors.CyanGlow.copy(alpha = 0.12f))
                                .border(0.8.dp, ClxyColors.CyanGlow.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "5GB Quota",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ClxyColors.CyanBright,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Text(
                        text = "چرخش هوشمند بین چند حساب بدون قطعی",
                        fontSize = 11.5.sp,
                        color = ClxyColors.TextDim,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showManualTokenDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.ObsidianCard)
                            .border(1.dp, ClxyColors.ObsidianBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = "Manual Token",
                            tint = ClxyColors.TextDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onAddAccount,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CyanBright.copy(alpha = 0.15f))
                            .border(1.dp, ClxyColors.CyanBright.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Account",
                            tint = ClxyColors.CyanBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Auto Failover Policy Card
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ClxyColors.ObsidianCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ClxyColors.ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ClxyColors.CyanBright.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        tint = ClxyColors.CyanBright,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "چرخش خودکار در سقف ۹۵٪",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "Auto-Failover at $threshold% allowance",
                                        fontSize = 10.5.sp,
                                        color = ClxyColors.TextDim,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ClxyColors.EmeraldGood.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "فعال",
                                        fontSize = 11.sp,
                                        color = ClxyColors.EmeraldGood,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = "به محض رسیدن مصرف هر اکانت به ۴.۷۵ گیگابایت، بدون قطع ارتباط تونل، شناسه نشست به اکانت بعدی چرخش پیدا می‌کند تا سقف مجانی تمدید شود.",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = ClxyColors.TextSecondary,
                            )
                        }
                    }
                }

                // Active Account Hero Card
                item {
                    val active = activeAccount
                    val (fraction, usageText) = Store.activeAccountQuotaUsage(context)

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ClxyColors.ObsidianCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ClxyColors.CyanBright.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(ClxyColors.SubcardSurface)
                                            .border(1.dp, ClxyColors.CyanBright.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = ClxyColors.CyanBright,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "حساب فعال تونل",
                                            fontSize = 11.sp,
                                            color = ClxyColors.TextDim,
                                        )
                                        Text(
                                            text = active?.email?.ifBlank { "Guest Pool Account" } ?: "Guest Pool Account",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ClxyColors.EmeraldGood.copy(alpha = 0.15f))
                                        .border(0.8.dp, ClxyColors.EmeraldGood.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "متصل",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ClxyColors.EmeraldGood
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Quota Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1E293B))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ClxyColors.CyanBright)
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = usageText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = "${(fraction * 100).toInt()}% مصرف شده",
                                    fontSize = 11.sp,
                                    color = ClxyColors.TextDim,
                                )
                            }
                        }
                    }
                }

                // Saved Accounts Section Title
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "لیست حساب‌های ذخیره شده (${accounts.size})",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClxyColors.TextSecondary
                        )
                    }
                }

                if (accounts.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ClxyColors.ObsidianCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ClxyColors.ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = ClxyColors.TextDim,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "حسابی هنوز ذخیره نشده است",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "با ورود به حساب CLXY Cloud، سهمیه ۵ گیگابایت برای هر اکانت فعال می‌شود.",
                                    fontSize = 11.5.sp,
                                    color = ClxyColors.TextDim,
                                    lineHeight = 16.sp
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = onAddAccount,
                                    colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CyanBright),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("ورود به حساب جدید", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    items(accounts) { account ->
                        val isActive = activeAccount?.email.equals(account.email, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isActive) ClxyColors.ObsidianCard else ClxyColors.ObsidianCard.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isActive) ClxyColors.CyanBright.copy(alpha = 0.4f) else ClxyColors.ObsidianBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isActive) ClxyColors.CyanBright else ClxyColors.TextDim)
                                    )
                                    Column {
                                        Text(
                                            text = account.email,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isActive) Color.White else ClxyColors.TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Text(
                                            text = account.quota.ifBlank { "5.0 GB Quota" },
                                            fontSize = 10.5.sp,
                                            color = ClxyColors.TextDim,
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ClxyColors.SubcardSurface)
                                                .border(1.dp, ClxyColors.ObsidianBorder, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    Store.activateAccount(context, account.email)
                                                    refresh()
                                                    Toast.makeText(context, "اکانت فعال شد", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("فعال‌سازی", fontSize = 11.sp, color = ClxyColors.CyanBright)
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            Store.removeAccount(context, account.email)
                                            refresh()
                                            Toast.makeText(context, "اکانت حذف شد", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = ClxyColors.TextDim,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(40.dp))
                }
            }
        }

        // Manual Token Dialog
        if (showManualTokenDialog) {
            var tokenInput by remember { mutableStateOf("") }
            var emailInput by remember { mutableStateOf("") }

            Dialog(onDismissRequest = { showManualTokenDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ClxyColors.ObsidianCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ClxyColors.CyanBright.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "ورود دستی نشست (Token)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            placeholder = { Text("ایمیل اکانت (مثال: user@clxy)", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ClxyColors.CyanBright,
                                unfocusedBorderColor = ClxyColors.ObsidianBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = { Text("توکن ۶۴ کاراکتری", fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ClxyColors.CyanBright,
                                unfocusedBorderColor = ClxyColors.ObsidianBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val t = tokenInput.trim()
                                    val e = emailInput.trim().ifBlank { "token_${t.take(6)}@clxy" }
                                    if (t.length >= 64) {
                                        Store.saveAccount(context, e, t)
                                        refresh()
                                        showManualTokenDialog = false
                                        Toast.makeText(context, "حساب با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "طول توکن معتبر نیست (حداقل ۶۴ نویسه)", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CyanBright),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ثبت و ذخیره", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { showManualTokenDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.SubcardSurface),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("انصراف", color = ClxyColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

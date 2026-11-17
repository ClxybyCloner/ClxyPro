package com.clxypro.vpn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxypro.vpn.Texts
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.ServerChoice
import com.clxypro.vpn.core.ServerOption
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.vpn.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The server choice card on HomeScreen: sleek cyber-neon card that opens
 * the revamped server picker sheet.
 */
@Composable
fun ServerChip(
    coreId: String,
    enabled: Boolean,
    onOpenAllServers: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var label by remember(coreId) { mutableStateOf(Prefs.serverLabel(coreId)) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ClxyColors.CardSurface.copy(alpha = if (enabled) 0.85f else 0.45f),
        border = BorderStroke(1.dp, ClxyColors.GlassBorder.copy(alpha = if (enabled) 0.9f else 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { open = true },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Texts.serverLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClxyColors.TextSecondary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label.ifBlank { Texts.auto },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) ClxyColors.Cyan else ClxyColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = if (enabled) ClxyColors.Cyan else ClxyColors.TextMuted,
            )
        }
    }

    if (open) {
        ServerPickerSheet(
            coreId = coreId,
            onDismiss = { open = false },
            onPick = { key, picked ->
                Prefs.setServer(coreId, key, picked)
                label = picked
                open = false
            },
            onOpenAllServers = onOpenAllServers?.let { action ->
                {
                    open = false
                    action()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPickerSheet(
    coreId: String,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit,
    onOpenAllServers: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val restored = remember { ServerChoice.lastTested(context, coreId) }
    var options by remember { mutableStateOf(restored ?: ServerChoice.options(context, coreId)) }
    val selected = remember { Prefs.server(coreId) }
    var refreshing by remember { mutableStateOf(false) }
    var tested by remember { mutableStateOf(restored != null) }
    var status by remember {
        mutableStateOf(
            when {
                restored == null -> ""
                restored.isEmpty() -> Texts.serverNoneReachable
                else -> Texts.serverTested(restored.size)
            }
        )
    }
    val stop = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose { stop.set(true) } }

    val shown = if (selected.isNotBlank() && options.none { it.key == selected }) {
        listOf(
            ServerOption(
                selected,
                Prefs.serverLabel(coreId).ifBlank { selected },
                "",
                reachable = if (tested) false else null,
            )
        ) + options
    } else {
        options
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0C101A),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ClxyBrandHeader(logoSize = 24.dp, showLabel = false)
                    Text(
                        text = Texts.serverTitle(Texts.coreName(coreId)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClxyColors.TextPrimary,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = ClxyColors.TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = if (coreId == "sstp") Texts.serverHintSstp else Texts.serverHint,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = ClxyColors.TextSecondary,
            )

            Spacer(Modifier.height(14.dp))

            // Action row: Download & Refresh Button + All Servers Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Download / Refresh Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (refreshing) ClxyColors.CardSurface else ClxyColors.Cyan.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            if (refreshing) ClxyColors.GlassBorder else ClxyColors.Cyan.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = !refreshing) {
                            refreshing = true
                            status = Texts.serverRefreshing
                            stop.set(false)
                            scope.launch {
                                val list = withContext(Dispatchers.IO) {
                                    ServerChoice.tested(context, coreId, { stop.get() }) {
                                        Logs.add(coreId, it)
                                    }
                                }
                                refreshing = false
                                tested = true
                                options = list
                                status = if (list.isEmpty()) {
                                    Texts.serverNoneReachable
                                } else {
                                    Texts.serverTested(list.size)
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = ClxyColors.Cyan,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Texts.serverRefreshing,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ClxyColors.TextSecondary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = ClxyColors.Cyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Texts.serverRefresh,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClxyColors.Cyan,
                        )
                    }
                }

                // If onOpenAllServers provided, show shortcut button
                if (onOpenAllServers != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenAllServers)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = ClxyColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "All Servers",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ClxyColors.TextSecondary,
                        )
                    }
                }
            }

            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status,
                    fontSize = 12.sp,
                    color = if (status == Texts.serverNoneReachable) Color(0xFFFF5252) else ClxyColors.Green,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Server items list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Auto Option Card
                item(key = "") {
                    AutoServerCard(
                        selected = selected.isBlank(),
                        onClick = { onPick("", "") },
                    )
                }

                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = Texts.serverEmpty,
                                fontSize = 13.sp,
                                color = ClxyColors.TextMuted,
                            )
                        }
                    }
                }

                items(shown, key = { it.key }) { option ->
                    ModernServerCard(
                        option = option,
                        selected = option.key == selected,
                        onClick = { onPick(option.key, option.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoServerCard(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) ClxyColors.Cyan else ClxyColors.GlassBorder
    val bgColor = if (selected) ClxyColors.Cyan.copy(alpha = 0.12f) else ClxyColors.CardSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ClxyColors.Cyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = ClxyColors.Cyan,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column {
                Text(
                    text = Texts.serverAuto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) ClxyColors.Cyan else ClxyColors.TextPrimary,
                )
                Text(
                    text = "Recommended · Automatic lowest latency",
                    fontSize = 11.sp,
                    color = ClxyColors.TextSecondary,
                )
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ClxyColors.Cyan),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ModernServerCard(
    option: ServerOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val flagCode = inferCountryCode(option.label, option.key)
    val borderColor = if (selected) ClxyColors.Cyan else ClxyColors.GlassBorder
    val bgColor = if (selected) ClxyColors.Cyan.copy(alpha = 0.12f) else ClxyColors.CardSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularCountryFlag(countryCode = flagCode, size = 36.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) ClxyColors.Cyan else ClxyColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (option.detail.isNotBlank()) {
                    Text(
                        text = option.detail,
                        fontSize = 11.sp,
                        color = ClxyColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (option.reachable == false) {
                Text(
                    text = Texts.serverUnreachable,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFF5252),
                )
            } else if (option.latencyMs >= 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    SignalStrengthBars(pingMs = option.latencyMs)
                    Text(
                        text = "${option.latencyMs} ms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClxyColors.Green,
                    )
                }
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(ClxyColors.Cyan),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

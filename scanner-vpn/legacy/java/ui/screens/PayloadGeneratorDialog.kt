package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class InjectionMethod(val label: String) {
    NORMAL("Normal"),
    FRONT_INJECT("Front Inject"),
    BACK_INJECT("Back Inject")
}

@Composable
fun PayloadGeneratorDialog(
    currentHost: String,
    onDismiss: () -> Unit,
    onPayloadGenerated: (String) -> Unit
) {
    var urlBugHost by remember { mutableStateOf(currentHost.ifBlank { "cdn.cloudflare.net" }) }
    var requestMethod by remember { mutableStateOf("GET") }
    var injectionMethod by remember { mutableStateOf(InjectionMethod.NORMAL) }

    var onlineHost by remember { mutableStateOf(true) }
    var forwardHost by remember { mutableStateOf(false) }
    var reverseProxy by remember { mutableStateOf(false) }
    var keepAlive by remember { mutableStateOf(true) }
    var userAgent by remember { mutableStateOf(true) }
    var wsUpgrade by remember { mutableStateOf(true) }

    val methods = listOf("GET", "POST", "HEAD", "CONNECT", "OPTIONS")
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Payload Generator",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Presets quick row
                Text(
                    text = "Quick Presets",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetPill(label = "Cloudflare WS", onClick = {
                        requestMethod = "GET"
                        injectionMethod = InjectionMethod.NORMAL
                        onlineHost = false
                        keepAlive = false
                        userAgent = false
                        wsUpgrade = true
                    }, modifier = Modifier.weight(1f))

                    PresetPill(label = "Front Inject", onClick = {
                        requestMethod = "GET"
                        injectionMethod = InjectionMethod.FRONT_INJECT
                        onlineHost = true
                        keepAlive = true
                        userAgent = true
                        wsUpgrade = true
                    }, modifier = Modifier.weight(1f))

                    PresetPill(label = "Direct Connect", onClick = {
                        requestMethod = "CONNECT"
                        injectionMethod = InjectionMethod.NORMAL
                        onlineHost = false
                        keepAlive = true
                        userAgent = false
                        wsUpgrade = false
                    }, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bug Host / URL Input
                OutlinedTextField(
                    value = urlBugHost,
                    onValueChange = { urlBugHost = it },
                    label = { Text("URL / Bug Host / SNI") },
                    placeholder = { Text("e.g. cdn.cloudflare.net") },
                    singleLine = true,
                    colors = customTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_payload_bughost")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Request Method Selector
                Text(
                    text = "Request Method",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    methods.forEach { method ->
                        val isSelected = requestMethod == method
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NeonGreen.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) NeonGreen else DarkSurfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { requestMethod = method }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = method,
                                color = if (isSelected) NeonGreen else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Injection Method Selector
                Text(
                    text = "Injection Method",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InjectionMethod.values().forEach { inj ->
                        val isSelected = injectionMethod == inj
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) ElectricBlue else DarkSurfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { injectionMethod = inj }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = inj.label,
                                color = if (isSelected) ElectricBlue else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Extra Headers Checkboxes
                Text(
                    text = "Extra Header Macros",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                HeaderCheckbox(label = "WebSocket Upgrade (Upgrade: websocket)", checked = wsUpgrade, onCheckedChange = { wsUpgrade = it })
                HeaderCheckbox(label = "Online Host (X-Online-Host)", checked = onlineHost, onCheckedChange = { onlineHost = it })
                HeaderCheckbox(label = "Forward Host (X-Forward-Host)", checked = forwardHost, onCheckedChange = { forwardHost = it })
                HeaderCheckbox(label = "Reverse Proxy (X-Forwarded-For)", checked = reverseProxy, onCheckedChange = { reverseProxy = it })
                HeaderCheckbox(label = "Keep-Alive (Connection: Keep-Alive)", checked = keepAlive, onCheckedChange = { keepAlive = it })
                HeaderCheckbox(label = "User-Agent Header", checked = userAgent, onCheckedChange = { userAgent = it })

                Spacer(modifier = Modifier.height(18.dp))

                // Generate Button
                Button(
                    onClick = {
                        val hostTarget = urlBugHost.ifBlank { "[host]" }
                        val sb = StringBuilder()

                        when (injectionMethod) {
                            InjectionMethod.FRONT_INJECT -> {
                                sb.append("$requestMethod http://$hostTarget/ HTTP/1.1[crlf]")
                                sb.append("Host: $hostTarget[crlf]")
                            }
                            InjectionMethod.BACK_INJECT -> {
                                sb.append("$requestMethod / HTTP/1.1[crlf]")
                                sb.append("Host: [host][crlf]")
                                sb.append("X-Online-Host: $hostTarget[crlf]")
                            }
                            InjectionMethod.NORMAL -> {
                                if (requestMethod == "CONNECT") {
                                    sb.append("CONNECT [host_port] HTTP/1.1[crlf]")
                                    sb.append("Host: $hostTarget[crlf]")
                                } else {
                                    sb.append("$requestMethod / HTTP/1.1[crlf]")
                                    sb.append("Host: $hostTarget[crlf]")
                                }
                            }
                        }

                        if (onlineHost && injectionMethod != InjectionMethod.BACK_INJECT) {
                            sb.append("X-Online-Host: $hostTarget[crlf]")
                        }
                        if (forwardHost) {
                            sb.append("X-Forward-Host: $hostTarget[crlf]")
                        }
                        if (reverseProxy) {
                            sb.append("X-Forwarded-For: $hostTarget[crlf]")
                        }
                        if (userAgent) {
                            sb.append("User-Agent: [ua][crlf]")
                        }
                        if (wsUpgrade) {
                            sb.append("Upgrade: websocket[crlf]")
                            sb.append("Connection: Upgrade[crlf]")
                            sb.append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==[crlf]")
                            sb.append("Sec-WebSocket-Version: 13[crlf]")
                        } else if (keepAlive) {
                            sb.append("Connection: Keep-Alive[crlf]")
                        }

                        sb.append("[crlf]")
                        onPayloadGenerated(sb.toString())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_generate_payload"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = DarkBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GENERATE PAYLOAD",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = NeonGreen,
                checkmarkColor = DarkBackground,
                uncheckedColor = DarkSurfaceBorder
            ),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PresetPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = ElectricBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

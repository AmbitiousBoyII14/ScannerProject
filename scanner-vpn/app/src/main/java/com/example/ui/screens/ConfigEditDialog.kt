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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ServerProtocol
import com.example.model.VpnConfig
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ConfigEditDialog(
    initialConfig: VpnConfig?,
    onDismiss: () -> Unit,
    onSave: (VpnConfig) -> Unit
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "") }
    var protocol by remember { mutableStateOf(initialConfig?.protocol ?: ServerProtocol.SSH_WEBSOCKET) }
    var host by remember { mutableStateOf(initialConfig?.host ?: "") }
    var portText by remember { mutableStateOf(initialConfig?.port?.toString() ?: "443") }
    var username by remember { mutableStateOf(initialConfig?.username ?: "") }
    var password by remember { mutableStateOf(initialConfig?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var wsPayloadPath by remember { mutableStateOf(initialConfig?.wsPayloadPath ?: "/dark-ws") }
    var customPayload by remember { mutableStateOf(initialConfig?.customPayload ?: "") }
    var sniServerName by remember { mutableStateOf(initialConfig?.sniServerName ?: "") }
    var isDefault by remember { mutableStateOf(initialConfig?.isDefault ?: false) }
    var isPasswordProtected by remember { mutableStateOf(initialConfig?.isPasswordProtected ?: false) }
    var configPassphrase by remember { mutableStateOf(initialConfig?.configPassphrase ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPayloadGenerator by remember { mutableStateOf(false) }

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
                    Text(
                        text = if (initialConfig == null) "New Configuration" else "Edit Configuration",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Config Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Config Name") },
                    singleLine = true,
                    colors = customTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_config_name")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Protocol Selector
                Text(
                    text = "Connection Protocol",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServerProtocol.values().take(3).forEach { proto ->
                        val isSelected = protocol == proto
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
                                .clickable { protocol = proto }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (proto) {
                                    ServerProtocol.SSH_WEBSOCKET -> "SSH+WS"
                                    ServerProtocol.SSH_DIRECT -> "SSH"
                                    ServerProtocol.WEBSOCKET_TLS -> "WS TLS"
                                    else -> proto.name
                                },
                                color = if (isSelected) NeonGreen else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Host and Port Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / Server IP") },
                        placeholder = { Text("sg1.server.com") },
                        singleLine = true,
                        colors = customTextFieldColors(),
                        modifier = Modifier
                            .weight(2f)
                            .testTag("input_config_host")
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_config_port")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Username & Password (SSH credentials)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("SSH Username") },
                    singleLine = true,
                    colors = customTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_config_username")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("SSH Password / Token") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility",
                                tint = TextMuted
                            )
                        }
                    },
                    colors = customTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_config_password")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // HTTP / WebSocket Custom Payload Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Payload (HTTP / WS)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Macros: [crlf], [host], [port], [ua]",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { showPayloadGenerator = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_open_payload_gen")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Generator",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = customPayload,
                    onValueChange = { customPayload = it },
                    placeholder = {
                        Text(
                            "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = customTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("input_config_payload"),
                    maxLines = 5,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // WebSocket Payload Path & SNI
                OutlinedTextField(
                    value = wsPayloadPath,
                    onValueChange = { wsPayloadPath = it },
                    label = { Text("WebSocket Path") },
                    placeholder = { Text("/dark-ws or /stream") },
                    singleLine = true,
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = sniServerName,
                    onValueChange = { sniServerName = it },
                    label = { Text("TLS / SNI Hostname (Optional)") },
                    placeholder = { Text("cdn.cloudflare.net") },
                    singleLine = true,
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Set Default Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Set as Default Config",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = NeonGreen
                        )
                    )
                }

                // Password Protection Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lock Config with Passphrase",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Switch(
                        checked = isPasswordProtected,
                        onCheckedChange = { isPasswordProtected = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = ElectricBlue
                        )
                    )
                }

                if (isPasswordProtected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = configPassphrase,
                        onValueChange = { configPassphrase = it },
                        label = { Text("Protection Passphrase") },
                        singleLine = true,
                        colors = customTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Save Action Button
                Button(
                    onClick = {
                        val parsedPort = portText.toIntOrNull() ?: 443
                        val newConfig = VpnConfig(
                            id = initialConfig?.id ?: ("cfg-" + System.currentTimeMillis()),
                            name = name.ifBlank { "Tunnel-$host" },
                            protocol = protocol,
                            host = host,
                            port = parsedPort,
                            username = username,
                            password = password,
                            wsPayloadPath = wsPayloadPath,
                            customPayload = customPayload,
                            sniServerName = sniServerName,
                            isDefault = isDefault,
                            isPasswordProtected = isPasswordProtected,
                            configPassphrase = configPassphrase
                        )
                        val validation = newConfig.validate()
                        if (!validation.isValid) {
                            errorMessage = validation.message
                        } else {
                            onSave(newConfig)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_config_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "SAVE CONFIGURATION",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    if (showPayloadGenerator) {
        PayloadGeneratorDialog(
            currentHost = host,
            onDismiss = { showPayloadGenerator = false },
            onPayloadGenerated = { gen ->
                customPayload = gen
                showPayloadGenerator = false
            }
        )
    }
}

@Composable
fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen,
    unfocusedBorderColor = DarkSurfaceBorder,
    focusedLabelColor = NeonGreen,
    unfocusedLabelColor = TextMuted,
    cursorColor = NeonGreen,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = DarkSurfaceElevated,
    unfocusedContainerColor = DarkSurfaceElevated
)

package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserSettings
import com.example.ui.components.CyberCard
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.CyberPurple
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
fun SettingsScreen(
    settings: UserSettings,
    onUpdateSettings: (UserSettings) -> Unit,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showServerSetupGuide by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tunnel protocol, network controls, and account management",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Account & Premium Status Banner
        CyberCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAccountDialog = true },
            borderColor = if (settings.isProUser) CyberPurple else ElectricBlue.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (settings.isProUser) CyberPurple else DarkSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (settings.isProUser) Icons.Default.Star else Icons.Default.AccountCircle,
                            contentDescription = "Account",
                            tint = if (settings.isProUser) Color.White else ElectricBlue,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = settings.accountTier,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (settings.isProUser) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NeonGreen)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = DarkBackground,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = settings.userEmail ?: "Tap to manage account & cloud sync",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Network & Tunnel Settings
        Text(
            text = "TUNNEL CONFIGURATION",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        CyberCard(modifier = Modifier.fillMaxWidth()) {
            // Auto Connect
            SettingToggleRow(
                icon = Icons.Default.VpnKey,
                title = "Auto-Connect on Launch",
                subtitle = "Automatically establish VPN when app is opened",
                checked = settings.autoConnectOnAppLaunch,
                onCheckedChange = {
                    onUpdateSettings(settings.copy(autoConnectOnAppLaunch = it))
                }
            )

            Divider(color = DarkSurfaceBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            // Reconnect on Network Change
            SettingToggleRow(
                icon = Icons.Default.Refresh,
                title = "Reconnect on Network Change",
                subtitle = "Seamlessly reconnect tunnel when switching Wi-Fi/LTE",
                checked = settings.reconnectOnNetworkChange,
                onCheckedChange = {
                    onUpdateSettings(settings.copy(reconnectOnNetworkChange = it))
                }
            )

            Divider(color = DarkSurfaceBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            // Kill Switch
            SettingToggleRow(
                icon = Icons.Default.Security,
                title = "Kill Switch (Block Leaks)",
                subtitle = "Prevent traffic outside tunnel if VPN drops",
                checked = settings.killSwitchEnabled,
                onCheckedChange = {
                    onUpdateSettings(settings.copy(killSwitchEnabled = it))
                }
            )

            Divider(color = DarkSurfaceBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            // DNS Provider
            SettingNavRow(
                icon = Icons.Default.Dns,
                title = "DNS Resolution Server",
                value = "${settings.primaryDns} (Cloudflare)",
                onClick = { showDnsDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: App Preferences & Guides
        Text(
            text = "GUIDES & INTEGRATIONS",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        CyberCard(modifier = Modifier.fillMaxWidth()) {
            SettingNavRow(
                icon = Icons.Default.Cloud,
                title = "Setup Self-Hosted SSH / WS Server",
                value = "View Instructions",
                onClick = { showServerSetupGuide = true }
            )

            Divider(color = DarkSurfaceBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            SettingNavRow(
                icon = Icons.Default.Language,
                title = "Language",
                value = settings.selectedLanguage,
                onClick = {
                    Toast.makeText(context, "English is active", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Support & Legal
        Text(
            text = "SUPPORT & ABOUT",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        CyberCard(modifier = Modifier.fillMaxWidth()) {
            SettingNavRow(
                icon = Icons.Default.HelpOutline,
                title = "Contact Support & Feedback",
                value = "support@darktunnelvpn.net",
                onClick = { showSupportDialog = true }
            )

            Divider(color = DarkSurfaceBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            SettingNavRow(
                icon = Icons.Default.Policy,
                title = "Privacy Policy",
                value = "Zero-Logs",
                onClick = { showPrivacyDialog = true }
            )

            Divider(color = DarkSurfaceBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            SettingNavRow(
                icon = Icons.Default.Info,
                title = "Terms of Service",
                value = "Fair Use",
                onClick = { showTermsDialog = true }
            )

            Divider(color = DarkSurfaceBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            SettingNavRow(
                icon = Icons.Default.Info,
                title = "About Scanner VPN",
                value = "v1.0.0 Pro",
                onClick = { showAboutDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Clear Data Button
        Button(
            onClick = { showClearDataConfirm = true },
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("clear_data_button")
        ) {
            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "RESET ALL CONFIGS & DATA", color = ErrorRed, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Account & Firebase sync dialog
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Scanner VPN Preview Account", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Current Status: ${settings.accountTier}",
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Firebase Auth and Firestore remote sync integration ready. Upgrading to VIP unlocks dedicated ultra-low latency nodes in Bangladesh, Singapore, and USA, and multi-device config sync.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!settings.isProUser) {
                        Button(
                            onClick = {
                                onUpdateSettings(settings.copy(isProUser = true, accountTier = "Dark Pro VIP", userEmail = "pro.user@darktunnelvpn.net"))
                                showAccountDialog = false
                                Toast.makeText(context, "VIP Pro Tier activated!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Activate VIP Access", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Close", color = TextPrimary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Setup Guide Dialog for self-hosted SSH/WS servers
    if (showServerSetupGuide) {
        AlertDialog(
            onDismissRequest = { showServerSetupGuide = false },
            title = { Text("Connecting Real Authorized Servers", color = TextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "To establish a 100% operational VPN tunnel through Scanner VPN:",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Server Daemon Requirements:\n" +
                                "• Any Linux VPS (Ubuntu/Debian) running OpenSSH on port 22 or 443.\n" +
                                "• Optional WebSocket proxy (e.g., Python WS or Nginx stream) listening on 443 with TLS.\n" +
                                "• BadVPN-udpgw on port 7300 for full UDP/gaming forwarding.\n\n" +
                                "2. Server Configuration in App:\n" +
                                "• In 'Configs' tab, tap '+' to add your server IP/hostname.\n" +
                                "• Enter your valid SSH username and password or authentication key.\n" +
                                "• If using Cloudflare CDN, specify the SNI (e.g., your domain) and WebSocket path (e.g. /dark-ws).\n\n" +
                                "3. Tap Connect on Home screen to initiate the tunnel handshake and route packets through your secured proxy.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerSetupGuide = false }) {
                    Text("Got It", color = NeonGreen)
                }
            },
            containerColor = DarkSurface
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Scanner VPN", color = TextPrimary) },
            text = {
                Column {
                    Text("Version: 1.0.0 Pro", color = NeonGreen, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Scanner VPN is a modern, high-speed Android VPN client engineered for SSH tunneling, WebSocket payload injection, and TLS/SNI obfuscation with zero logging.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = TextPrimary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy (Zero-Logs)", color = TextPrimary) },
            text = {
                Text(
                    "Scanner VPN strictly adheres to a zero-logs policy. We do not inspect, log, or record your browsing traffic, DNS queries, visited destinations, or personal connection history on remote infrastructure. All tunnel configurations and session logs reside strictly in your private on-device encrypted storage.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close", color = TextPrimary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Terms of Service Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service", color = TextPrimary) },
            text = {
                Text(
                    "By using Scanner VPN, you agree to only connect to authorized servers that you own or have explicit administrative permission to access. Unlawful use, malicious network scanning, or unauthorized network interception is strictly prohibited.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Accept", color = NeonGreen)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Support Dialog
    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text("Customer Support", color = TextPrimary) },
            text = {
                Column {
                    Text("Support Team: support@darktunnelvpn.net", color = ElectricBlue)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Community Telegram: @DarkTunnelVpnOfficial", color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Report server downtime, request new locations, or get configuration assistance 24/7.", color = TextMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupportDialog = false }) {
                    Text("Done", color = TextPrimary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // DNS Dialog
    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = { Text("DNS Provider", color = TextPrimary) },
            text = {
                Column {
                    val dnsList = listOf(
                        "Cloudflare (1.1.1.1 / 1.0.0.1)" to "1.1.1.1",
                        "Google DNS (8.8.8.8 / 8.8.4.4)" to "8.8.8.8",
                        "AdGuard Privacy (94.140.14.14)" to "94.140.14.14",
                        "Quad9 Secure (9.9.9.9)" to "9.9.9.9"
                    )
                    dnsList.forEach { (label, ip) ->
                        val isSelected = settings.primaryDns == ip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(settings.copy(primaryDns = ip))
                                    showDnsDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) NeonGreen else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Clear Data Confirmation
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Reset App Data?", color = TextPrimary) },
            text = { Text("This will remove all saved configurations, history logs, and reset settings to default values.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllData()
                        showClearDataConfirm = false
                        Toast.makeText(context, "All configuration data reset", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NeonGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkBackground,
                checkedTrackColor = NeonGreen
            )
        )
    }
}

@Composable
fun SettingNavRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricBlue,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VpnConfig
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
fun ConfigManagerScreen(
    configs: List<VpnConfig>,
    activeConfig: VpnConfig?,
    onSelectConfig: (VpnConfig) -> Unit,
    onSaveConfig: (VpnConfig) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onSetDefaultConfig: (String) -> Unit,
    onImportJson: (String, String) -> Result<VpnConfig>,
    onExportJson: (VpnConfig) -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingConfig by remember { mutableStateOf<VpnConfig?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportingConfig by remember { mutableStateOf<VpnConfig?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Configurations",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage SSH, WebSocket & TLS tunnel profiles",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Import button
                IconButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                        .testTag("open_import_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Import Config",
                        tint = ElectricBlue
                    )
                }

                // Add button
                IconButton(
                    onClick = {
                        editingConfig = null
                        showEditDialog = true
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonGreen)
                        .testTag("open_add_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Config",
                        tint = DarkBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Configs List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(configs, key = { it.id }) { config ->
                val isSelected = activeConfig?.id == config.id
                val validation = config.validate()

                CyberCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectConfig(config) },
                    borderColor = if (isSelected) NeonGreen else DarkSurfaceBorder,
                    borderWidth = if (isSelected) 1.5.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = config.name,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (config.isDefault) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(NeonGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "DEFAULT",
                                            color = NeonGreen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (config.isPasswordProtected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Password Protected",
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${config.protocol.displayName} • ${config.host}:${config.port}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            if (config.sniServerName.isNotBlank()) {
                                Text(
                                    text = "SNI: ${config.sniServerName}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            if (config.customPayload.isNotBlank()) {
                                Text(
                                    text = "Payload: ${config.customPayload.take(35)}...",
                                    color = NeonGreen.copy(alpha = 0.85f),
                                    fontSize = 10.sp
                                )
                            } else {
                                Text(
                                    text = "Payload: Default WS Upgrade",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Health validation status
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (validation.isValid) Icons.Default.Verified else Icons.Default.Tune,
                                    contentDescription = "Validation",
                                    tint = if (validation.isValid) ConnectedGreen else ErrorRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = validation.message,
                                    color = if (validation.isValid) ConnectedGreen else ErrorRed,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Actions column
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Star default toggle
                            IconButton(
                                onClick = { onSetDefaultConfig(config.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (config.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Toggle Default",
                                    tint = if (config.isDefault) NeonGreen else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Export button
                            IconButton(
                                onClick = { exportingConfig = config },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = "Export Config",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Edit button
                            IconButton(
                                onClick = {
                                    editingConfig = config
                                    showEditDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Config",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Delete button (prevent deleting if only 1 config)
                            if (configs.size > 1) {
                                IconButton(
                                    onClick = { onDeleteConfig(config.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Config",
                                        tint = ErrorRed.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit/Create Dialog
    if (showEditDialog) {
        ConfigEditDialog(
            initialConfig = editingConfig,
            onDismiss = { showEditDialog = false },
            onSave = { saved ->
                onSaveConfig(saved)
                showEditDialog = false
                Toast.makeText(context, "Config saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        ConfigImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { payload, pass ->
                val result = onImportJson(payload, pass)
                if (result.isSuccess) {
                    showImportDialog = false
                    Toast.makeText(context, "Configuration imported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // Export Dialog
    exportingConfig?.let { config ->
        val jsonPayload = onExportJson(config)
        ConfigExportDialog(
            config = config,
            exportedPayload = jsonPayload,
            onDismiss = { exportingConfig = null }
        )
    }
}

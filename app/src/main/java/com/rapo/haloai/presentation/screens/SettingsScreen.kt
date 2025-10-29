package com.rapo.haloai.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rapo.haloai.presentation.viewmodel.ModelsViewModel
import com.rapo.haloai.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    modelsViewModel: ModelsViewModel = hiltViewModel()
) {
    val theme by settingsViewModel.theme.collectAsState()
    val hardwareAcceleration by settingsViewModel.hardwareAcceleration.collectAsState()
    val memoryMode by settingsViewModel.memoryMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { modelsViewModel.importModelFromPath(it.toString()) }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = theme,
            onThemeSelected = { settingsViewModel.setTheme(it) },
            onDismiss = { showThemeDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Model Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            SettingItem(
                title = "Load from Device",
                subtitle = "Import a model from local storage",
                icon = Icons.Default.FolderOpen,
                onClick = { filePicker.launch("*/*") }
            )
        }
        item {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            SettingItem(
                title = "Theme",
                subtitle = theme.replaceFirstChar { it.uppercase() },
                icon = Icons.Default.DarkMode,
                onClick = { showThemeDialog = true }
            )
        }
        item {
            Text("Performance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            SettingItem(
                title = "Hardware Acceleration",
                subtitle = if (hardwareAcceleration) "Enabled" else "Disabled",
                icon = Icons.Default.Speed,
                onClick = { settingsViewModel.setHardwareAcceleration(!hardwareAcceleration) }
            )
        }
        item {
            SettingItem(
                title = "Memory Mode",
                subtitle = memoryMode,
                icon = Icons.Default.Memory,
                onClick = { /* TODO: Implement memory mode selection dialog */ }
            )
        }
        item {
            Text("About", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ThemeSelectionDialog(currentTheme: String, onThemeSelected: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                val themes = listOf("system", "light", "dark")
                themes.forEach { theme ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme); onDismiss() } 
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onThemeSelected(theme); onDismiss() }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(theme.replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = { }
    )
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Open")
        }
    }
}

package com.rapo.haloai.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Experimental", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Experimental Features",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                Text(
                    "These features are experimental and may not work as expected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            item { 
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                ExperimentalFeatureCard(
                    title = "Voice Cloning",
                    description = "Experiment with voice synthesis",
                    icon = Icons.Default.RecordVoiceOver
                )
            }
            
            item {
                ExperimentalFeatureCard(
                    title = "Character Chat Modes",
                    description = "Chat with different AI personalities",
                    icon = Icons.Default.Person
                )
            }
            
            item {
                ExperimentalFeatureCard(
                    title = "GPU Inference Test",
                    description = "Test hardware acceleration performance",
                    icon = Icons.Default.Speed
                )
            }
            
            item {
                ExperimentalFeatureCard(
                    title = "Developer Console",
                    description = "View model logs and debug information",
                    icon = Icons.Default.BugReport
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalFeatureCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { Toast.makeText(context, "$title is not yet implemented", Toast.LENGTH_SHORT).show() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

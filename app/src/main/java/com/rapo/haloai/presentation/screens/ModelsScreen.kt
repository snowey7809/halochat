package com.rapo.haloai.presentation.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.rapo.haloai.data.database.entities.ModelFormat
import com.rapo.haloai.presentation.viewmodel.ModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
    navController: NavController
) {
    val context = LocalContext.current
    val models by viewModel.models.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val navigateToChat by viewModel.navigateToChat.collectAsState()
    
    // File picker for local GGUF models
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                // Get file name from URI
                val fileName = getFileNameFromUri(context, uri) ?: "imported_model.gguf"
                
                // Copy file to app's models directory
                val modelsDir = java.io.File(context.filesDir, "models")
                if (!modelsDir.exists()) modelsDir.mkdirs()
                
                val destFile = java.io.File(modelsDir, fileName)
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (destFile.exists() && destFile.length() > 0) {
                    // Import the copied file
                    viewModel.importModelFromPath(destFile.absolutePath)
                } else {
                    // Error will be handled by ViewModel
                }
            } catch (e: Exception) {
                // Error will be handled by ViewModel
            }
        }
    }
    
    // Curated models from Hugging Face
    val curatedModels = remember {
        listOf(
            CuratedModel(
                name = "Phi-3 Mini (Recommended)",
                fileName = "Phi-3-mini-4k-instruct-q4.gguf",
                repoId = "microsoft/Phi-3-mini-4k-instruct-gguf",
                format = ModelFormat.GGUF,
                params = "3.8B",
                description = "Small, fast, and efficient for mobile"
            ),
            CuratedModel(
                name = "Gemma 2B",
                fileName = "gemma-2b-it-q4_0.gguf",
                repoId = "google/gemma-2b-it-gguf",
                format = ModelFormat.GGUF,
                params = "2B",
                description = "Lightweight model by Google"
            ),
            CuratedModel(
                name = "Qwen 2.5 0.5B",
                fileName = "qwen2.5-0.5b-instruct-q4_0.gguf",
                repoId = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                format = ModelFormat.GGUF,
                params = "0.5B",
                description = "Ultra-light model for basic tasks"
            ),
            CuratedModel(
                name = "TinyLlama",
                fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                repoId = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
                format = ModelFormat.GGUF,
                params = "1.1B",
                description = "Smallest model for testing"
            )
        )
    }
    
    // Show error snackbar
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(importStatus) {
        if (importStatus?.startsWith("Model imported") == true) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearImportStatus()
        }
    }

    // Auto-navigate to chat screen when model is ready
    LaunchedEffect(navigateToChat) {
        if (navigateToChat != null) {
            val (modelId, modelName) = navigateToChat
            // Clear the navigation state immediately to prevent repeated navigations
            viewModel.clearNavigationState()
            // Navigate to chat screen - the ChatViewModel will handle loading the model
            navController.navigate("chat")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Models", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            filePicker.launch(arrayOf("*/*"))
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Import Local Model")
                    }
                }
            )
        },
        snackbarHost = {
            if (errorMessage != null || importStatus != null) {
                Snackbar {
                    Text(errorMessage ?: importStatus ?: "")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Installed models section
            if (models.isNotEmpty()) {
                item {
                    Text(
                        "Installed Models",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(models) { model ->
                    InstalledModelCard(
                        model = model,
                        onDelete = { viewModel.deleteModel(model.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
            
            // Curated models section
            item {
                Text(
                    if (models.isEmpty()) "Download a Model to Get Started" else "Available Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            items(curatedModels) { model ->
                CuratedModelCard(
                    model = model,
                    isDownloading = downloadProgress?.first == model.repoId,
                    progress = if (downloadProgress?.first == model.repoId) downloadProgress?.second ?: 0 else 0,
                    onDownload = {
                        viewModel.downloadModel(model.repoId, model.fileName, model.format)
                    }
                )
            }
        }
    }
}

fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun InstalledModelCard(
    model: com.rapo.haloai.data.database.entities.ModelEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${model.format} • ${formatBytes(model.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (model.tokensPerSecond != null) {
                    Text(
                        text = "${model.tokensPerSecond} tokens/sec",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun CuratedModelCard(
    model: CuratedModel,
    isDownloading: Boolean,
    progress: Int,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${model.params} parameters • ${model.format}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isDownloading) {
                Column {
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloading: $progress%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download")
                }
            }
        }
    }
}

data class CuratedModel(
    val name: String,
    val fileName: String,
    val repoId: String,
    val format: ModelFormat,
    val params: String,
    val description: String
)

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

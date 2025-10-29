package com.rapo.haloai.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.rapo.haloai.presentation.viewmodel.ChatViewModel
import com.halilibo.richtext.ui.material3.Material3RichText
import com.halilibo.richtext.markdown.Markdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    navController: NavController,
    onMenuClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val currentMessage by viewModel.currentMessage.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val streamedResponse by viewModel.streamedResponse.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val showReloadDialog by viewModel.showReloadModelDialog.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val generationSpeed by viewModel.generationSpeed.collectAsState()
    val contextUsed by viewModel.contextUsed.collectAsState()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    
    // Track if user is near bottom (more lenient)
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            // Consider "near bottom" if last visible item is within 2 items of the end
            lastVisibleItem?.let { it.index >= totalItems - 3 } ?: true
        }
    }
    
    // Track last scroll position to detect upward scrolls
    var lastScrollIndex by remember { mutableStateOf(0) }
    var shouldAutoScroll by remember { mutableStateOf(true) }

    // Auto-scroll on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isGenerating) {
            listState.scrollToItem(messages.size - 1)
            shouldAutoScroll = true
        }
    }
    
    // Auto-scroll during generation
    LaunchedEffect(isGenerating, streamedResponse.length) {
        if (isGenerating && streamedResponse.isNotEmpty() && shouldAutoScroll) {
            // Scroll every 50 chars to reduce jank
            if (streamedResponse.length % 50 == 0) {
                // Scroll to the streaming message (after all saved messages)
                listState.scrollToItem(messages.size)
            }
        }
    }
    
    // Detect scroll position changes
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { currentIndex ->
                if (listState.isScrollInProgress) {
                    // User scrolled up significantly - disable auto-scroll
                    if (currentIndex < lastScrollIndex - 1) {
                        shouldAutoScroll = false
                    }
                    lastScrollIndex = currentIndex
                }
            }
    }
    
    // Resume autoscroll when user scrolls back to bottom
    LaunchedEffect(isNearBottom, isGenerating) {
        if (isNearBottom && isGenerating) {
            shouldAutoScroll = true
        }
    }
    
    // Model reload confirmation dialog
    if (showReloadDialog) {
        ModelReloadConfirmationDialog(
            onConfirm = { viewModel.confirmModelReload() },
            onDismiss = { viewModel.cancelModelReload() }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatHistorySidebar(
                sessions = sessions,
                currentSessionId = currentSessionId,
                onSessionClick = { sessionId ->
                    viewModel.switchToSession(sessionId)
                    coroutineScope.launch { drawerState.close() }
                },
                onNewChatClick = {
                    viewModel.createNewChat()
                    coroutineScope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
                onNavigateToModels = {
                    navController.navigate("models")
                    coroutineScope.launch { drawerState.close() }
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Halo Chat", fontWeight = FontWeight.Bold)
                        if (selectedModel != null) {
                            Text(
                                text = "Powered by ${selectedModel!!.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            drawerState.open()
                        }
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = "History")
                    }
                },
                actions = {
                    // New Chat button
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.createNewChat()
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat")
                    }
                    
                    // Delete Chat button
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.clearChat()
                    }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Chat")
                    }
                    
                    // Settings button
                    var showSettings by remember { mutableStateOf(false) }
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSettings = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Chat Settings")
                    }
                    
                    if (showSettings) {
                        ChatSettingsDialog(
                            onDismiss = { showSettings = false },
                            viewModel = viewModel
                        )
                    }
                }
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    // Context warning banner
                    val contextMax = viewModel.generationSettings.value.contextLength
                    if (contextUsed > 0) {
                        ContextWarningBanner(contextUsed, contextMax)
                    }
                    
                    // Real-time metrics (show during/after generation)
                    if (isGenerating || generationSpeed > 0f) {
                        GenerationMetrics(generationSpeed, contextUsed, contextMax)
                    }
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(messages) { message ->
                            ChatMessageItem(
                                message = message,
                                onDelete = { viewModel.deleteMessage(message) },
                                onEdit = { newContent ->
                                    viewModel.updateMessage(message, newContent)
                                }
                            )
                        }
                        
                        // Show streaming response while generating
                        if (isGenerating && streamedResponse.isNotEmpty()) {
                            item {
                                StreamingMessageItem(
                                    content = streamedResponse,
                                    isThinking = streamedResponse.length < 10
                                )
                            }
                        } else if (isGenerating) {
                            item {
                                ThinkingIndicator()
                            }
                        }
                    }
                    
                    ChatInputArea(
                        message = currentMessage,
                        onMessageChange = { viewModel.updateMessage(it) },
                        onSend = { viewModel.sendMessage() },
                        enabled = !isGenerating && selectedModel != null
                    )
                }
                
                // Floating scroll-to-bottom button
                if (!isNearBottom && (isGenerating || messages.isNotEmpty())) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(if (isGenerating) messages.size else messages.size - 1)
                                shouldAutoScroll = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 80.dp, end = 16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom"
                        )
                    }
                }
            }
        }
    )
    }
}

@Composable
fun ChatHistorySidebar(
    sessions: List<com.rapo.haloai.data.database.entities.ChatSessionEntity>,
    currentSessionId: String,
    onSessionClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
        ) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Halo Chat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNewChatClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Chat")
                    }
                }
            }
            
            HorizontalDivider()
            
            // Session list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        "CHAT HISTORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                
                items(sessions) { session ->
                    SessionItem(
                        session = session,
                        isSelected = session.sessionId == currentSessionId,
                        onClick = { onSessionClick(session.sessionId) },
                        onDelete = { onDeleteSession(session.sessionId) }
                    )
                }
            }
            
            HorizontalDivider()
            
            // Navigation items
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                    label = { Text("Models") },
                    selected = false,
                    onClick = onNavigateToModels
                )
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = onNavigateToSettings
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionItem(
    session: com.rapo.haloai.data.database.entities.ChatSessionEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteConfirm = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.secondaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = formatTimestamp(session.lastMessageAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (showDeleteConfirm) {
                IconButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> "${diff / 604800_000}w ago"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageItem(
    message: com.rapo.haloai.data.database.entities.ChatEntity,
    onDelete: () -> Unit,
    onEdit: (String) -> Unit = {}
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    // Extract content without metadata
    val messageContent = if (isUser) {
        message.content
    } else {
        message.content.substringBefore("\n\n---\n")
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Render markdown for AI responses, plain text for user
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    // Split message content and metadata footer
                    val contentParts = message.content.split("\n\n---\n")
                    val actualContent = contentParts[0]
                    val metadataFooter = if (contentParts.size > 1) contentParts[1] else null
                    
                    Material3RichText(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Markdown(
                            content = actualContent
                        )
                    }
                    
                    // Show metadata footer separately if present
                    if (metadataFooter != null && message.stopReason != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Stop reason with icon
                        val (icon, text, color) = when (message.stopReason) {
                            "eos_token" -> Triple("🛑", "End of sequence", MaterialTheme.colorScheme.tertiary)
                            "max_tokens_reached" -> Triple("⚠️", "Max tokens (${message.tokenCount})", MaterialTheme.colorScheme.error)
                            else -> Triple("✓", "Completed", MaterialTheme.colorScheme.primary)
                        }
                        
                        Text(
                            text = "$icon $text",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = color
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Performance metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (message.tokenCount != null) {
                                Text(
                                    text = "${message.tokenCount} tokens",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (message.responseTimeMs != null) {
                                Text(
                                    text = "${String.format("%.1f", message.responseTimeMs / 1000f)}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (message.tokensPerSecond != null) {
                                Text(
                                    text = "${String.format("%.1f", message.tokensPerSecond)} tok/s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // Copy option (for both user and AI messages)
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(messageContent))
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    }
                )
                
                // Share and Edit options (only for user messages)
                if (isUser) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            showEditDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, messageContent)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share message"))
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    )
                }
                
                // Delete option
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }
        
        // Edit dialog for user messages
        if (showEditDialog && isUser) {
            var editText by remember { mutableStateOf(messageContent) }
            
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Message") },
                text = {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 10
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (editText.isNotBlank()) {
                                onEdit(editText)
                            }
                            showEditDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun StreamingMessageItem(
    content: String,
    isThinking: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isThinking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (content.isNotEmpty()) {
                    // Render markdown for streaming response
                    Material3RichText(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Markdown(
                            content = content
                        )
                    }
                }
                
                // Blinking cursor
                if (content.length >= 10) {
                    var showCursor by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(500)
                            showCursor = !showCursor
                        }
                    }
                    
                    if (showCursor) {
                        Text(
                            text = "▋",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Halo Chat is thinking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ChatInputArea(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type your message...") },
                maxLines = 4,
                enabled = enabled
            )
            
            FloatingActionButton(
                onClick = {
                    if (enabled && message.isNotBlank()) {
                        onSend()
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ChatSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: ChatViewModel
) {
    val settings by viewModel.generationSettings.collectAsState()
    var systemPromptText by remember { mutableStateOf(settings.systemPrompt) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generation Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // System Prompt
                Text("System Prompt", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = systemPromptText,
                    onValueChange = { 
                        systemPromptText = it
                        viewModel.updateSystemPrompt(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("You are a helpful AI assistant.") },
                    minLines = 3,
                    maxLines = 5
                )
                
                // Auto-configure button
                Button(
                    onClick = {
                        viewModel.autoConfigureFromModel()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-Configure from Model")
                }
                
                HorizontalDivider()
                
                // Max Tokens
                Text("Max Tokens: ${settings.maxTokens}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Tokens per response (higher = longer answers)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.maxTokens.toFloat(),
                    onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                    valueRange = 128f..4096f,
                    steps = 0
                )
                
                // Temperature
                Text("Temperature: ${"%.2f".format(settings.temperature)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Lower = focused, Higher = creative",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.temperature,
                    onValueChange = { viewModel.updateTemperature(it) },
                    valueRange = 0.1f..2.0f
                )
                
                HorizontalDivider()
                
                // CPU Threads
                var tempThreads by remember(settings.threads) { mutableStateOf(settings.threads) }
                Text("CPU Threads: $tempThreads", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Requires model reload",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = tempThreads.toFloat(),
                    onValueChange = { tempThreads = it.toInt() },
                    onValueChangeFinished = {
                        if (tempThreads != settings.threads) {
                            viewModel.updateThreads(tempThreads)
                        }
                    },
                    valueRange = 1f..7f,
                    steps = 5
                )
                
                // Context Length
                var tempContext by remember(settings.contextLength) { mutableStateOf(settings.contextLength) }
                Text("Context Length: $tempContext", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Total context window (Requires model reload)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = tempContext.toFloat(),
                    onValueChange = { tempContext = it.toInt() },
                    onValueChangeFinished = {
                        if (tempContext != settings.contextLength) {
                            viewModel.updateContextLength(tempContext)
                        }
                    },
                    valueRange = 1024f..8192f,
                    steps = 7
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.resetSettings()
                onDismiss()
            }) {
                Text("Reset")
            }
        }
    )
}

@Composable
fun ModelReloadConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Reload Model Required", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This setting change requires reloading the model.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "• The current chat will be saved\n• Model will be unloaded\n• New settings will apply on next generation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reload Model")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ContextWarningBanner(contextUsed: Int, contextMax: Int) {
    val percent = (contextUsed * 100f) / contextMax
    
    if (percent > 70) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (percent > 90) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (percent > 90) Icons.Default.Error else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (percent > 90) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = if (percent > 90)
                        "⚠️ Context almost full! Clear chat to continue."
                    else
                        "Context filling up (${percent.toInt()}%). Consider starting new chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (percent > 90)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun GenerationMetrics(speed: Float, contextUsed: Int, contextMax: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val color = when {
                    speed > 10f -> Color(0xFF4CAF50) // Green
                    speed > 5f -> Color(0xFFFFC107) // Yellow
                    speed > 2f -> Color(0xFFFF9800) // Orange
                    else -> Color(0xFFF44336) // Red
                }
                
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = color)
                }
                
                Text(
                    text = "⚡ ${String.format("%.1f", speed)} tok/s",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Context usage
            val contextPercent = (contextUsed * 100f) / contextMax
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        contextPercent < 50 -> Color(0xFF4CAF50)
                        contextPercent < 80 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }
                )
                
                Text(
                    text = "$contextUsed/$contextMax (${contextPercent.toInt()}%)",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

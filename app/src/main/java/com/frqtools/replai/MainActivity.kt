@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.frqtools.replai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frqtools.replai.data.Category
import com.frqtools.replai.data.Reply
import com.frqtools.replai.ui.ReplyViewModel
import com.frqtools.replai.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.compose.foundation.gestures.detectDragGestures
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.google.android.gms.ads.MobileAds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path

class MainActivity : ComponentActivity() {
    companion object {
        val initialSharedText = androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            Log.e("MainActivity", "MobileAds init error", e)
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            initialSharedText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        enableEdgeToEdge()
        setContent {
            val viewModel: ReplyViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = isDark) {
                ReplaiApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            initialSharedText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}

/**
 * Main application container.
 * Handles splash screen triggers and active screen states.
 */
@Composable
fun ReplaiApp(viewModel: ReplyViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSplash by remember { mutableStateOf(true) }

    // Observe ViewModel Toast messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForBackupOnFirstLaunch(context)
    }

    // Splash Timer
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        showSplash = false
    }

    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Crossfade(
            targetState = showSplash,
            animationSpec = tween(300),
            label = "SplashToMain"
        ) { isSplash ->
            if (isSplash) {
                SplashScreen()
            } else {
                if (!onboardingCompleted) {
                    OnboardingScreen(onComplete = { viewModel.completeOnboarding() })
                } else {
                    MainAppLayout(viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * Premium graphic splash screen centered with key branding details.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF070B14), Color(0xFF111827)),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Logo Image Card
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .background(Color(0xFF1F2937), RoundedCornerShape(24.dp))
                    .border(2.dp, Brush.radialGradient(listOf(Color(0xFF3B82F6), Color.Transparent)), RoundedCornerShape(24.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.replai_icon),
                    contentDescription = "Replai Icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name Display
            Text(
                text = "Replai",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-1.5).sp,
                fontFamily = FontFamily.SansSerifier
            )

            // Play Store Secondary Branded description string
            Text(
                text = "Quick Reply & Saved Messages",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3B82F6),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tagline
            Text(
                text = "Your most-used messages — one tap away, in any app.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9CA3AF),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
                lineHeight = 20.sp
            )
        }
    }
}

// Global safe font family fallback
private val FontFamily.Companion.SansSerifier: FontFamily
    get() = FontFamily.SansSerif

/**
 * Main Layout containing Tabs and Floating Action Button (FAB).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainAppLayout(viewModel: ReplyViewModel) {
    val categories by viewModel.categories.collectAsState()
    val replies by viewModel.allReplies.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredReplies by viewModel.filteredReplies.collectAsState()
    val categorySortOrder by viewModel.categorySortOrder.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Saved Replies, 1 = AI Prompts, 2 = Settings
    var activeCategoryFilter by remember { mutableStateOf<Category?>(null) }

    val context = LocalContext.current
    var showCloudSyncDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab) {
        activeCategoryFilter = null
    }

    val filteredCategoriesForTab = remember(categories, activeTab) {
        categories.filter { it.isForAi == (activeTab == 1) }
    }

    // Scroll States
    val repliesListState = rememberLazyListState()
    val aiPromptsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Create file launcher for native JSON export downloading
    val exportLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val exportString = viewModel.exportToJson()
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(exportString.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "System backup exported successfully! 💾", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Open file launcher for native JSON import uploading
    val importLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                        val success = viewModel.importFromJson(jsonString)
                        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, "Config loaded! Re-imported templates. ✅", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Restoration failed. Invalid backup schema. ❌", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Multi-select templates state
    val selectedReplyIds = remember { mutableStateListOf<Long>() }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    // Form settings
    var isAddingAiPrompt by remember { mutableStateOf(false) }

    // Dialog state controllers
    var showAddReplyDialog by remember { mutableStateOf(false) }
    var initialContentText by remember { mutableStateOf("") }

    val sharedText = MainActivity.initialSharedText.value
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            initialContentText = sharedText
            showAddReplyDialog = true
            MainActivity.initialSharedText.value = null
        }
    }

    var replyToEdit by remember { mutableStateOf<Reply?>(null) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showReorganizeDialog by remember { mutableStateOf(false) }
    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            selectedReplyIds.clear()
        } else if (activeCategoryFilter != null) {
            activeCategoryFilter = null
        } else if (activeTab != 0) {
            activeTab = 0
        } else {
            showExitConfirmationDialog = true
        }
    }

    // Variable interpolation prompt controller
    var activeVariableReply by remember { mutableStateOf<Reply?>(null) }

    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /**
     * Copy function with analytical Variable scanner checks.
     */
    val triggerCopyAndUsage: (Reply) -> Unit = { reply ->
        val variables = extractVariables(reply.content)
        if (variables.isNotEmpty()) {
            activeVariableReply = reply
        } else {
            // Direct copy
            try {
                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            } catch (e: Exception) {
                Log.e("MainActivity", "Haptic error", e)
            }
            val clip = ClipData.newPlainText("Replai Clipboard", reply.content)
            systemClipboard.setPrimaryClip(clip)
            viewModel.incrementUsage(reply.id)
            Toast.makeText(context, "Copied content: \"${reply.content.take(45)}\"...", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().testTag("tabs_row")
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = {
                            if (activeTab == 0) {
                                scope.launch { repliesListState.animateScrollToItem(0) }
                            } else {
                                activeTab = 0
                                isMultiSelectMode = false
                                selectedReplyIds.clear()
                            }
                        },
                        icon = { Icon(Icons.Default.Email, contentDescription = "Replies Tab") },
                        label = { Text("Replies", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = {
                            if (activeTab == 1) {
                                scope.launch { aiPromptsListState.animateScrollToItem(0) }
                            } else {
                                activeTab = 1
                                isMultiSelectMode = false
                                selectedReplyIds.clear()
                            }
                        },
                        icon = { Icon(Icons.Default.Star, contentDescription = "AI Prompts Tab") },
                        label = { Text("AI Prompts", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = {
                            activeTab = 2
                            isMultiSelectMode = false
                            selectedReplyIds.clear()
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings Tab") },
                        label = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            // Floating Action Button for adding replies (Tab 0 & 1)
            if (activeTab < 2) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isAddingAiPrompt = (activeTab == 1)
                        showAddReplyDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("floating_add_button"),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Saved Material"
                        )
                    },
                    text = {
                        Text(
                            text = if (activeTab == 1) "Add AI Prompt" else "Add Reply",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Elegant top branding status bar
            TopBrandHeader(
                replies = replies,
                viewModel = viewModel,
                onCloudClick = { showCloudSyncDialog = true }
            )

            if (showCloudSyncDialog) {
                LocalBackupRestoreDialog(
                    viewModel = viewModel,
                    onDismiss = { showCloudSyncDialog = false },
                    onExportClick = {
                        exportLauncher.launch("replai_backup_download.json")
                    },
                    onImportClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                )
            }

            val showRestorePrompt by viewModel.showRestorePrompt.collectAsState()
            val backupDateString by viewModel.backupDateString.collectAsState()
            if (showRestorePrompt) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissRestorePrompt() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Backup found icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Backup?", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "An automatic backup from $backupDateString was found on your device storage! 💾",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Would you like to restore your categories and replies to get all your templates back immediately?",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.restoreFromAutoBackup(context)
                            }
                        ) {
                            Text("Restore Backup", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.dismissRestorePrompt() }
                        ) {
                            Text("Start Fresh")
                        }
                    }
                )
            }

            // Multi-Select Banner overlay
            AnimatedVisibility(
                visible = isMultiSelectMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                selectedReplyIds.clear()
                                isMultiSelectMode = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close selection mode")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${selectedReplyIds.size} Selected",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val activeTemplates = if (activeTab == 0) {
                                filteredReplies.filter { !it.isAiPrompt }
                            } else {
                                filteredReplies.filter { it.isAiPrompt }
                            }
                            val allInTabSelected = activeTemplates.isNotEmpty() && activeTemplates.all { it.id in selectedReplyIds }
                            TextButton(onClick = {
                                if (allInTabSelected) {
                                    activeTemplates.forEach { selectedReplyIds.remove(it.id) }
                                } else {
                                    activeTemplates.forEach {
                                        if (it.id !in selectedReplyIds) {
                                            selectedReplyIds.add(it.id)
                                        }
                                    }
                                }
                            }) {
                                Text(if (allInTabSelected) "Deselect All" else "Select All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.deleteReplies(selectedReplyIds.toList())
                                    selectedReplyIds.clear()
                                    isMultiSelectMode = false
                                },
                                enabled = selectedReplyIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = if (selectedReplyIds.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar (visible on Home/AI Prompts Tab when NOT multi-selecting)
            if (activeTab < 2 && !isMultiSelectMode) {
                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) }
                )
            }

            // Categories Filter Bar (visible on Home/AI Prompts when NOT multi-selecting)
            if (activeTab < 2 && !isMultiSelectMode) {
                CategoryFilterBar(
                    categories = filteredCategoriesForTab,
                    activeCategoryFilter = activeCategoryFilter,
                    onCategoryFilterSelect = { activeCategoryFilter = it },
                    onAddCategoryClick = { showAddCategoryDialog = true },
                    onCategoryLongClick = { categoryToEdit = it },
                    categorySortOrder = categorySortOrder,
                    onCategorySortChange = { viewModel.setCategorySortOrder(it) },
                    onManualSortClick = { showReorganizeDialog = true }
                )
            }

            // Tab contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> {
                        // REPLIES TAB
                        val visibleReplies = remember(filteredReplies, activeCategoryFilter) {
                            val nonAi = filteredReplies.filter { !it.isAiPrompt }
                            val currentFilter = activeCategoryFilter
                            if (currentFilter != null) {
                                nonAi.filter { it.categoryId == currentFilter.id }
                            } else {
                                nonAi
                            }
                        }
                        RepliesTabContent(
                            categories = categories,
                            replies = visibleReplies,
                            lazyListState = repliesListState,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedReplyIds = selectedReplyIds,
                            onToggleSelect = { id ->
                                if (id in selectedReplyIds) selectedReplyIds.remove(id) else selectedReplyIds.add(id)
                            },
                            onEnterMultiSelect = { id ->
                                isMultiSelectMode = true
                                if (id !in selectedReplyIds) selectedReplyIds.add(id)
                            },
                            onItemClick = { reply ->
                                if (isMultiSelectMode) {
                                    if (reply.id in selectedReplyIds) selectedReplyIds.remove(reply.id) else selectedReplyIds.add(reply.id)
                                } else {
                                    triggerCopyAndUsage(reply)
                                }
                            },
                            onItemLongClick = { reply ->
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedReplyIds.add(reply.id)
                                }
                            },
                            onItemEditClick = { reply ->
                                replyToEdit = reply
                            },
                            onPinToggle = { viewModel.togglePinReply(it) }
                        )
                    }
                    1 -> {
                        // AI PROMPTS TAB
                        val visibleAiPrompts = remember(filteredReplies, activeCategoryFilter) {
                            val ai = filteredReplies.filter { it.isAiPrompt }
                            val currentFilter = activeCategoryFilter
                            if (currentFilter != null) {
                                ai.filter { it.categoryId == currentFilter.id }
                            } else {
                                ai
                            }
                        }
                        AiPromptsTabContent(
                            categories = categories,
                            replies = visibleAiPrompts,
                            lazyListState = aiPromptsListState,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedReplyIds = selectedReplyIds,
                            onToggleSelect = { id ->
                                if (id in selectedReplyIds) selectedReplyIds.remove(id) else selectedReplyIds.add(id)
                            },
                            onEnterMultiSelect = { id ->
                                isMultiSelectMode = true
                                if (id !in selectedReplyIds) selectedReplyIds.add(id)
                            },
                            onItemClick = { reply ->
                                if (isMultiSelectMode) {
                                    if (reply.id in selectedReplyIds) selectedReplyIds.remove(reply.id) else selectedReplyIds.add(reply.id)
                                } else {
                                    triggerCopyAndUsage(reply)
                                }
                            },
                            onItemLongClick = { reply ->
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedReplyIds.add(reply.id)
                                }
                            },
                            onItemEditClick = { reply ->
                                replyToEdit = reply
                            },
                            onAddPromptClick = {
                                isAddingAiPrompt = true
                                showAddReplyDialog = true
                            },
                            onPinToggle = { viewModel.togglePinReply(it) }
                        )
                    }
                    2 -> {
                        // SETTINGS TAB
                        SettingsBackupTabContent(
                            viewModel = viewModel,
                            repliesCount = replies.size,
                            categoriesCount = categories.size,
                            onManageCategoriesClick = { showReorganizeDialog = true }
                        )
                    }
                }
            }
        }

        // DIALOGS SECTION

        // 1. Variable Picker Dialog
        activeVariableReply?.let { reply ->
            VariablePromptDialog(
                reply = reply,
                onDismiss = { activeVariableReply = null },
                onCopied = { expandedText ->
                    // Set to clipboard
                    try {
                        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Haptic error", e)
                    }
                    val clip = ClipData.newPlainText("Replai Clipboard", expandedText)
                    systemClipboard.setPrimaryClip(clip)
                    viewModel.incrementUsage(reply.id)
                    Toast.makeText(context, "Variables replaced and message copied!", Toast.LENGTH_SHORT).show()
                    activeVariableReply = null
                }
            )
        }

        // 2. Add / Edit Reply Dialog
        if (showAddReplyDialog || replyToEdit != null) {
            AddEditReplyDialog(
                reply = replyToEdit,
                categories = categories,
                isAiPromptDefault = if (replyToEdit != null) replyToEdit!!.isAiPrompt else isAddingAiPrompt,
                defaultCategoryId = activeCategoryFilter?.id,
                initialContent = initialContentText,
                checkForDuplicate = { t, c -> viewModel.checkForDuplicate(t, c) },
                onDismiss = {
                    showAddReplyDialog = false
                    replyToEdit = null
                    initialContentText = ""
                },
                onSave = { categoryId, title, content, isAiPrompt ->
                    if (replyToEdit != null) {
                        viewModel.updateReply(replyToEdit!!, title, content, categoryId, isAiPrompt)
                    } else {
                        viewModel.addReply(categoryId, title, content, isAiPrompt)
                    }
                    showAddReplyDialog = false
                    replyToEdit = null
                    initialContentText = ""
                },
                onDelete = { reply ->
                    viewModel.deleteReply(reply)
                    showAddReplyDialog = false
                    replyToEdit = null
                    initialContentText = ""
                }
            )
        }

        // 3. New Category Dialog
        if (showAddCategoryDialog) {
            AddEditCategoryDialog(
                category = null,
                isAiPromptDefault = (activeTab == 1),
                onDismiss = { showAddCategoryDialog = false },
                onSave = { name, icon, isForAi, color ->
                    viewModel.addCategory(name, icon, isForAi = isForAi, colorHex = color)
                    showAddCategoryDialog = false
                },
                onDelete = {}
            )
        }

        // 4. Edit/Delete Category Dialog
        categoryToEdit?.let { cat ->
            AddEditCategoryDialog(
                category = cat,
                isAiPromptDefault = (activeTab == 1),
                onDismiss = { categoryToEdit = null },
                onSave = { name, icon, isForAi, color ->
                    viewModel.updateCategory(cat, name, icon, isForAi, color)
                    categoryToEdit = null
                },
                onDelete = {
                    categoryToDelete = cat
                    categoryToEdit = null
                }
            )
        }

        // 5. Category Reorder Dialog
        if (showReorganizeDialog) {
            CategoryReorderDialog(
                categories = filteredCategoriesForTab,
                onDismiss = { showReorganizeDialog = false },
                onSaveOrder = { updatedList ->
                    viewModel.updateCategoriesOrder(updatedList)
                    showReorganizeDialog = false
                },
                onEditCategory = { categoryToEdit = it },
                onDeleteCategory = {
                    categoryToDelete = it
                }
            )
        }

        // 5.5. Delete Category Confirmation Dialog
        categoryToDelete?.let { cat ->
            AlertDialog(
                onDismissRequest = { categoryToDelete = null },
                title = { Text("Delete Category? 🗑️", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete the category \"${cat.name}\"?\n\n⚠️ WARNING: All reply templates and AI prompt templates in this category will be PERMANENTLY deleted.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteCategory(cat)
                            categoryToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { categoryToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 6. Exit Confirmation Dialog
        if (showExitConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showExitConfirmationDialog = false },
                title = { Text("Exit Replai? 🛑", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to exit and close the application?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitConfirmationDialog = false
                            (context as? android.app.Activity)?.finish()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Exit", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Top brand Header block.
 */
@Composable
fun TopBrandHeader(
    replies: List<Reply>,
    viewModel: ReplyViewModel,
    onCloudClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.replai_icon),
                            contentDescription = "Header Logo",
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Replai",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                }
                Text(
                    text = "Saved Messages & Quick Actions",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Local Storage Backup Icon Button at top right
            IconButton(
                onClick = onCloudClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Storage backup settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun LocalBackupRestoreDialog(
    viewModel: ReplyViewModel,
    onDismiss: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Backup icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Storage & Backups",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Replai works completely offline. All your templates are stored directly on your phone, keeping your data private and secure. No internet required!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                // WhatsApp-Style Automatic Backup Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "📲 Real-Time Local Syncing",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Every time you save, update, or reorganize a template, Replai instantly secures a copy inside your device's documents folder.\n\nIf you delete and reinstall Replai, the app will auto-detect this backup on startup and prompt to restore your complete data safely!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Quick Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Update Auto Backup Button
                    Button(
                        onClick = {
                            viewModel.triggerLocalAutoBackup()
                            Toast.makeText(context, "Backup updated at Documents/ReplaiBackup/replai_auto_backup.json! 💾", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Auto-Backup to Phone", fontSize = 12.sp)
                    }

                    // Download Backup File Button
                    OutlinedButton(
                        onClick = {
                            onExportClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Download backup file", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Backup File (.json)", fontSize = 12.sp)
                    }

                    // Restore Backup File Button
                    OutlinedButton(
                        onClick = {
                            onImportClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Select & restore backup file", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Backup File from Phone", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Filter search bar component with real-time text listener.
 */
@Composable
fun SearchBarComponent(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search by titles, replies or tags...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "SearchIcon") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear text")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("main_search_bar"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
            )
        )
    }
}

/**
 * Separate, modular filter bar for choosing template categories.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryFilterBar(
    categories: List<Category>,
    activeCategoryFilter: Category?,
    onCategoryFilterSelect: (Category?) -> Unit,
    onAddCategoryClick: () -> Unit,
    onCategoryLongClick: (Category) -> Unit,
    categorySortOrder: String,
    onCategorySortChange: (String) -> Unit,
    onManualSortClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Show All" item
        item {
            FilterChip(
                selected = activeCategoryFilter == null,
                onClick = { onCategoryFilterSelect(null) },
                label = { Text("All Templates") },
                leadingIcon = { Icon(Icons.Default.Home, modifier = Modifier.size(16.dp), contentDescription = "All items") }
            )
        }

        // Listed categories
        items(categories) { category ->
            val isSelected = activeCategoryFilter?.id == category.id
            val catColor = try {
                Color(android.graphics.Color.parseColor(category.colorHex))
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }
            FilterChip(
                selected = isSelected,
                onClick = { onCategoryFilterSelect(category) },
                label = {
                    Text(
                        text = category.name,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = getIconForName(category.iconName),
                        contentDescription = category.name,
                        tint = if (isSelected) Color.White else catColor,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = if (isSelected) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = catColor,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
                border = FilterChipDefaults.filterChipBorder(
                    selected = isSelected,
                    enabled = true,
                    borderColor = catColor.copy(alpha = 0.4f),
                    selectedBorderColor = catColor
                ),
                modifier = Modifier.combinedClickable(
                    onClick = { onCategoryFilterSelect(category) },
                    onLongClick = { onCategoryLongClick(category) }
                )
            )
        }

        // Create new category item pill
        item {
            AssistChip(
                onClick = onAddCategoryClick,
                label = { Text("+ Category", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.Add, modifier = Modifier.size(16.dp), contentDescription = "Create category") },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        // Sort categories button
        item {
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Sort categories",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Default Order") },
                        leadingIcon = { Icon(Icons.Default.Menu, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onCategorySortChange("order")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("A to Z") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onCategorySortChange("alphabetical")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Z to A") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onCategorySortChange("alphabetical_desc")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Manage & Reorder Categories ⚙️") },
                        leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onManualSortClick()
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Content screen for Quick Replies tab.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepliesTabContent(
    categories: List<Category>,
    replies: List<Reply>,
    lazyListState: LazyListState = rememberLazyListState(),
    isMultiSelectMode: Boolean = false,
    selectedReplyIds: List<Long> = emptyList(),
    onToggleSelect: (Long) -> Unit = {},
    onEnterMultiSelect: (Long) -> Unit = {},
    onItemClick: (Reply) -> Unit,
    onItemLongClick: (Reply) -> Unit,
    onItemEditClick: (Reply) -> Unit,
    onPinToggle: (Reply) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (replies.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty list icon",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No saved replies found",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Tap the '+' floating button at the bottom right or create categories to start saving templates.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp).widthIn(max = 260.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("replies_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(replies, key = { it.id }) { reply ->
                    val category = remember(categories, reply.categoryId) {
                        categories.firstOrNull { it.id == reply.categoryId }
                    }
                    ReplyCardItem(
                        reply = reply,
                        category = category,
                        onClick = { onItemClick(reply) },
                        onLongClick = { onItemLongClick(reply) },
                        onPinToggle = { onPinToggle(reply) },
                        isMultiSelectMode = isMultiSelectMode,
                        isSelected = reply.id in selectedReplyIds,
                        onEditClick = { onItemEditClick(reply) }
                    )
                }
            }
        }
    }
}

/**
 * Visual grouping subheader inside list.
 */
@Composable
fun CategorySubheader(categoryName: String, categoryIcon: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = getIconForName(categoryIcon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = categoryName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = "$count item" + (if (count == 1) "" else "s"),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

/**
 * Visual Individual saved card item.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReplyCardItem(
    reply: Reply,
    category: Category?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPinToggle: () -> Unit,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val variables = extractVariables(reply.content)
    val hasVariables = variables.isNotEmpty()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 1.dp else 2.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (isSelected) 1.dp else 2.dp, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("reply_card_${reply.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Header details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reply.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // VARIABLE AND USAGE LABELS AND EDIT ACTION
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (reply.isPinned) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        if (reply.usageCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Used times",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = reply.usageCount.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (hasVariables) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = "Has inputs",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Template",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }

                        if (!isMultiSelectMode) {
                            IconButton(
                                onClick = onPinToggle,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Toggle Pin",
                                    tint = if (reply.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        if (onEditClick != null && !isMultiSelectMode) {
                            IconButton(
                                onClick = onEditClick,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Saved Reply",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Body text
                Text(
                    text = reply.content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                // Category display badge below body text
                category?.let { cat ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = getIconForName(cat.iconName),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = cat.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tap prompt bar
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasVariables) "⚡ Tap to compile & hold to edit" else "⚡ Tap once to copy & hold to edit",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                        if (hasVariables) {
                            Text(
                                text = "Requires: ${variables.joinToString()}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    val charCount = reply.content.length
                    val wordCount = reply.content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                    Text(
                        text = "${charCount} chars • ${wordCount} words",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Content screen for AI Prompts panel tab.
 */
@Composable
fun AiPromptsTabContent(
    categories: List<Category>,
    replies: List<Reply>,
    lazyListState: LazyListState = rememberLazyListState(),
    isMultiSelectMode: Boolean = false,
    selectedReplyIds: List<Long> = emptyList(),
    onToggleSelect: (Long) -> Unit = {},
    onEnterMultiSelect: (Long) -> Unit = {},
    onItemClick: (Reply) -> Unit,
    onItemLongClick: (Reply) -> Unit,
    onItemEditClick: (Reply) -> Unit,
    onAddPromptClick: () -> Unit,
    onPinToggle: (Reply) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (replies.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Empty prompt state icon",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No saved AI prompts",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Button(
                        onClick = onAddPromptClick,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text("Create prompt now")
                    }
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("ai_prompts_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(replies, key = { it.id }) { reply ->
                    val category = remember(categories, reply.categoryId) {
                        categories.firstOrNull { it.id == reply.categoryId }
                    }
                    ReplyCardItem(
                        reply = reply,
                        category = category,
                        onClick = { onItemClick(reply) },
                        onLongClick = { onItemLongClick(reply) },
                        onPinToggle = { onPinToggle(reply) },
                        isMultiSelectMode = isMultiSelectMode,
                        isSelected = reply.id in selectedReplyIds,
                        onEditClick = { onItemEditClick(reply) }
                    )
                }
            }
        }
    }
}

/**
 * Settings, Guides & Backups sync area content.
 */
@Composable
fun SettingsBackupTabContent(
    viewModel: ReplyViewModel,
    repliesCount: Int,
    categoriesCount: Int,
    onManageCategoriesClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val themeMode by viewModel.themeMode.collectAsState()
    val replySortOrder by viewModel.replySortOrder.collectAsState()

    val categories by viewModel.categories.collectAsState()
    var bulkText by remember { mutableStateOf("") }
    var tempSelectedCategory by remember { mutableStateOf<Category?>(null) }
    var isAiPromptImport by remember { mutableStateOf(false) }
    var bulkCategoryDropdownExpanded by remember { mutableStateOf(false) }

    // Create file launcher for native JSON export downloading
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val exportString = viewModel.exportToJson()
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(exportString.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "System backup exported successfully! 💾", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Open file launcher for native JSON import uploading
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                        val success = viewModel.importFromJson(jsonString)
                        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, "Config loaded! Re-imported templates. ✅", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Restoration failed. Invalid backup schema. ❌", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_scroll_surface"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SECTION 1: HOW IT WORKS GUIDE
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "How Replai Works 📖",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                InstructionBullet(
                    stepNum = "1",
                    title = "Receive message",
                    description = "A customer messages you in Instagram DMs, WhatsApp, or Telegram requesting payment options."
                )
                InstructionBullet(
                    stepNum = "2",
                    title = "Switch & Copy",
                    description = "Open Replai, find the 'Standard pricing' or 'Direct Bank' template and hit once to copy."
                )
                InstructionBullet(
                    stepNum = "3",
                    title = "Instant variables",
                    description = "Fill any placeholders like {name} or {amount} in our dialogue pop-up, then click Copy Personal Message."
                )
                InstructionBullet(
                    stepNum = "4",
                    title = "Paste & send details",
                    description = "Switch back to the client discussion box, hit Paste and Send. Your response takes 4 seconds instead of 45!"
                )
            }
        }

        // SECTION 2: APP PREFERENCES AND THEME MODES
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Application Settings ⚙️",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Theme mode selection Row
                Column {
                    Text(
                        text = "Preferred Color Theme",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (modeKey, modeLabel) ->
                            val isSelected = themeMode == modeKey
                            Button(
                                onClick = { viewModel.setThemeMode(modeKey) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text(modeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Default sorting of replies list
                Column {
                    Text(
                        text = "Template Listing Sort Order",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("recent" to "Recent", "alphabetical" to "A-Z", "usage" to "Popular").forEach { (sortKey, sortLabel) ->
                            val isSelected = replySortOrder == sortKey
                            Button(
                                onClick = { viewModel.setReplySortOrder(sortKey) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text(sortLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Offline Backup & Storage Settings Section
                Column {
                    Text(
                        text = "💾 Offline Storage & Backup",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Replai automatically secures your saved messages completely offline on your device storage. No cloud servers are used, meaning your data is 100% private.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.triggerLocalAutoBackup()
                            Toast.makeText(context, "Backup updated at Documents/ReplaiBackup/replai_auto_backup.json! 💾", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Auto-Backup to Phone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // SECTION 3: METRICS & BACKUPS INDEX CARD WITH NATIVE FILE SAF
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Statistics 📈",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatisticDisplayBlock(title = "Categories", value = categoriesCount.toString())
                    StatisticDisplayBlock(title = "Templates", value = repliesCount.toString())
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onManageCategoriesClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Manage categories icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manage & Sort Categories 📁", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Team Backups & Sync (JSON) 📥",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Restore or share communications by exporting configuration files natively to your device's Downloads folder.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            exportLauncher.launch("replai_backup_${System.currentTimeMillis()}.json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export file", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export to File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Import file", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import from File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // SECTION 3.5: BULK TEXT IMPORT
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Bulk Setup Helper & Excel Importer 🚀",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Instantly copy rows directly from Excel, CSV, or Google Sheets and paste them here to import multiple templates at once.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 16.sp
                )

                // Select Standard Saved Reply vs AI prompt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val labelReplies = "Saved Replies"
                    val labelAi = "AI Prompts"
                    
                    listOf(false to labelReplies, true to labelAi).forEach { (isAi, label) ->
                        val isSelected = isAiPromptImport == isAi
                        Button(
                            onClick = { 
                                isAiPromptImport = isAi 
                                tempSelectedCategory = null // reset selection when mode changes
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Selected Category Selector dropdown
                val filteredCats = categories.filter { it.isForAi == isAiPromptImport }
                val activeSelectedCat = tempSelectedCategory?.takeIf { it.isForAi == isAiPromptImport } ?: filteredCats.firstOrNull()

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { bulkCategoryDropdownExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = getIconForName(activeSelectedCat?.iconName ?: "folder"),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Target Category: ${activeSelectedCat?.name ?: "Select..."}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Open categories")
                        }
                    }

                    DropdownMenu(
                        expanded = bulkCategoryDropdownExpanded,
                        onDismissRequest = { bulkCategoryDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        if (filteredCats.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No categories in this section yet") },
                                onClick = { bulkCategoryDropdownExpanded = false }
                            )
                        } else {
                            filteredCats.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        tempSelectedCategory = category
                                        bulkCategoryDropdownExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(getIconForName(category.iconName), contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }

                // Input box for bulk text
                OutlinedTextField(
                    value = bulkText,
                    onValueChange = { bulkText = it },
                    label = { Text("Paste spreadsheet cells or text templates") },
                    placeholder = {
                        Text(
                            text = if (isAiPromptImport) {
                                "Excel Columns: Title | Prompt. For example:\nGrammar Fix\tAct as a writer..."
                            } else {
                                "Excel/Sheets format:\nSupport Greeting\tWelcome to Replai!\nPersonalized Wave\tHello {name}!"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    minLines = 4,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )

                // Detailed formatting guide
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("💡 Formatting Options:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Excel / Google Sheets: Select column A (Title) and B (Content), copy, and paste directly! Separated by Tab.", fontSize = 10.sp)
                        Text("• CSV / Pipe Format: Column A, Column B (or split by '|' or standard commas).", fontSize = 10.sp)
                        Text("• Divider Block: Title on line 1, content below, separated by '---'.", fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = {
                        val finalCat = activeSelectedCat
                        if (finalCat == null) {
                            Toast.makeText(context, "Please create or select a target category first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (bulkText.trim().isEmpty()) {
                            Toast.makeText(context, "Please paste some template messages first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.bulkImportReplies(
                            categoryId = finalCat.id,
                            inputText = bulkText,
                            isAiPrompt = isAiPromptImport,
                            onComplete = { count ->
                                if (count > 0) {
                                    bulkText = ""
                                }
                            }
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import from Excel / Clipboard", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // SECTION 4: ABOUT APP & DEVELOPER
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "About App & Developer ℹ️",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Replai represents a system-wide quick communication clipboard companion designed by FRQ TOOLS & Services to help you reply to client messages in seconds.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Developer & Company Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Developer info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("FRQ TOOLS & Services", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Creator & Maintainer", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Interactive Info Buttons
                DeveloperSupportRow(
                    label = "Whatsapp Support",
                    value = "+92 325 2604441",
                    icon = Icons.Default.Call,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/923252604441"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open WhatsApp.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                DeveloperSupportRow(
                    label = "Email Support",
                    value = "frqtools@gmail.com",
                    icon = Icons.Default.Email,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:frqtools@gmail.com"))
                            intent.putExtra(Intent.EXTRA_SUBJECT, "Replai App Support")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intentFallback = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:frqtools@gmail.com"))
                                context.startActivity(intentFallback)
                            } catch (x: Exception) {
                                Toast.makeText(context, "No email client found.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                DeveloperSupportRow(
                    label = "Official Website",
                    value = "frqtools.com",
                    icon = Icons.Default.Home,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://frqtools.com"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open web browser.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                DeveloperSupportRow(
                    label = "Free Online Tools",
                    value = "frqtools.com/tools",
                    icon = Icons.Default.Build,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://frqtools.com/tools"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open web browser.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Actions: Share & More Apps options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                val shareText = "Improve your client communications with Replai! Save template answers, auto-interpolate placeholder fields, and speed up reply times. Download the official app here: https://play.google.com/store/apps/details?id=com.frqtools.replai"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Replai App"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not launch sharing panel.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share App", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/dev?id=5671242375404230146"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open developer page.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "More Apps", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("More Apps", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                
                // Made with Love footer with heartbeat symbol
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Made with ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "heart",
                        tint = Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = " by FRQ TOOLS • v1.0.1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun StatisticDisplayBlock(title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = value,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun DeveloperSupportRow(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Navigate to link",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun InstructionBullet(stepNum: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNum,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Dialog enabling variable substitutions for templates on the fly.
 */
@Composable
fun VariablePromptDialog(
    reply: Reply,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val variableList = remember(reply.content) { extractVariables(reply.content) }
    val variableMap = remember { mutableStateMapOf<String, String>() }

    // Initialize map
    LaunchedEffect(reply.id) {
        variableList.forEach { variableMap[it] = "" }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Personalize Reply ⚡",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = reply.title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Variables builder text fields
                variableList.forEach { variable ->
                    Text(
                        text = "Variable: {$variable}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    OutlinedTextField(
                        value = variableMap[variable] ?: "",
                        onValueChange = { variableMap[variable] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp)
                            .testTag("var_input_$variable"),
                        placeholder = { Text("E.g., John, $49...", fontSize = 13.sp) },
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live preview banner
                val interpolatedPreview = remember(variableMap.entries.toList(), reply.content) {
                    interpolateVariables(reply.content, variableMap)
                }

                Text(
                    text = "Live Preview:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = interpolatedPreview,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val finalMsg = interpolateVariables(reply.content, variableMap)
                            onCopied(finalMsg)
                        },
                        modifier = Modifier.testTag("copy_prompt_apply_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Personal Message")
                    }
                }
            }
        }
    }
}

/**
 * Compact Add/Edit Dialog sheet overlays.
 */
@Composable
fun AddEditReplyDialog(
    reply: Reply?,
    categories: List<Category>,
    isAiPromptDefault: Boolean = false,
    defaultCategoryId: Long? = null,
    initialContent: String = "",
    checkForDuplicate: ((title: String, content: String) -> Boolean)? = null,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long, title: String, content: String, isAiPrompt: Boolean) -> Unit,
    onDelete: (Reply) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf(reply?.title ?: "") }
    var content by remember { mutableStateOf(reply?.content ?: (if (initialContent.isNotEmpty()) initialContent else "")) }
    val isAiPrompt = remember { reply?.isAiPrompt ?: isAiPromptDefault }
    var selectedCategory by remember {
        mutableStateOf(
            categories.firstOrNull { it.id == reply?.categoryId }
                ?: categories.firstOrNull { it.id == defaultCategoryId }
                ?: categories.firstOrNull()
        )
    }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (reply != null) "Edit Template ✏️" else "New Template ➕",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (reply != null) {
                        IconButton(onClick = { onDelete(reply) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // AI vs Standard Mode Indicator Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isAiPrompt) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, if (isAiPrompt) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAiPrompt) Icons.Default.Star else Icons.Default.Email,
                            contentDescription = null,
                            tint = if (isAiPrompt) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAiPrompt) "AI Prompt Template Mode" else "Standard Quick Reply Mode",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAiPrompt) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_item_title_input"),
                    singleLine = true
                )

                // Category selector spinner
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { categoryDropdownExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_item_category_spinner")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = getIconForName(selectedCategory?.iconName ?: "folder"),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Category: ${selectedCategory?.name ?: "Select..."}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Open categories")
                        }
                    }

                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category
                                    categoryDropdownExpanded = false
                                },
                                leadingIcon = {
                                    Icon(getIconForName(category.iconName), contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                // Reply Message Content textbox (Auto-expanding with minLines)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Message template content") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 320.dp)
                        .testTag("add_item_content_input"),
                    minLines = 6,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Variables use {name} syntax", fontSize = 11.sp)
                            Text("${content.length} characters", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val catId = selectedCategory?.id ?: categories.firstOrNull()?.id ?: 1L
                            if (title.isNotBlank() && content.isNotBlank()) {
                                val isDup = if (reply == null) {
                                    checkForDuplicate?.invoke(title, content) == true
                                } else {
                                    (reply.title != title || reply.content != content) && checkForDuplicate?.invoke(title, content) == true
                                }

                                if (isDup) {
                                    android.widget.Toast.makeText(context, "A template with this title and content already exists!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    onSave(catId, title, content, isAiPrompt)
                                }
                            }
                        },
                        enabled = title.isNotBlank() && content.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_item_save_button")
                    ) {
                        Text("Save Template", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Category editor dialog.
 */
@Composable
fun AddEditCategoryDialog(
    category: Category?,
    isAiPromptDefault: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, isForAi: Boolean, colorHex: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(category?.iconName ?: "folder") }
    var isForAi by remember { mutableStateOf(category?.isForAi ?: isAiPromptDefault) }
    var selectedColorHex by remember { mutableStateOf(category?.colorHex ?: "#6200EE") }

    val iconChoices = listOf("folder", "star", "home", "email", "credit_card", "event", "map")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (category != null) "Edit Category 📁" else "Add Category 📁",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    if (category != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = Color.Red)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category name") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Toggle for AI category status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Prompt Tab Category",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "If enabled, this category belongs to the AI Prompts tab; otherwise, Saved Replies.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = isForAi,
                        onCheckedChange = { isForAi = it },
                        modifier = Modifier.testTag("is_for_ai_switch")
                    )
                }

                Text(
                    text = "Select Category Icon:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Row of icons choice selector
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(iconChoices) { iconName ->
                        val isSelected = selectedIcon == iconName
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                                .border(
                                    BorderStroke(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedIcon = iconName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getIconForName(iconName),
                                contentDescription = iconName,
                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text(
                    text = "Select Category Accent Color:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                val colorsPreset = listOf(
                    "#6200EE", // Purple
                    "#3700B3", // Indigo
                    "#03DAC6", // Teal
                    "#FF0266", // Pink
                    "#FF9100", // Orange
                    "#00E5FF", // Cyan
                    "#00E676", // Green
                    "#FF3D00"  // Red-Orange
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(colorsPreset) { colorString ->
                        val isColorSelected = selectedColorHex == colorString
                        val col = try {
                            Color(android.graphics.Color.parseColor(colorString))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    BorderStroke(
                                        width = if (isColorSelected) 3.dp else 0.dp,
                                        color = if (isColorSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
                                    ),
                                    CircleShape
                                )
                                .clickable { selectedColorHex = colorString },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isColorSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (colorString == "#03DAC6" || colorString == "#00E5FF" || colorString == "#00E676") Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name, selectedIcon, isForAi, selectedColorHex)
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

/**
 * Safe mapper function mapping category string names to vector core graphics icons.
 */
fun getIconForName(name: String): ImageVector {
    return when (name) {
        "home" -> Icons.Default.Home
        "star" -> Icons.Default.Star
        "email" -> Icons.Default.Email
        "credit_card" -> Icons.Default.PlayArrow       // Using standard compile safe icons
        "event" -> Icons.Default.DateRange
        "map" -> Icons.Default.LocationOn
        "waving_hand" -> Icons.Default.Home
        "payments" -> Icons.Default.Star
        "local_shipping" -> Icons.Default.Send
        "psychology" -> Icons.Default.Favorite
        else -> Icons.Default.Star
    }
}

/**
 * Variable extraction utilities scanning `{value}` parameters using regex engines.
 */
fun extractVariables(content: String): List<String> {
    val variableRegex = """\{([^{}]+)\}""".toRegex()
    return variableRegex.findAll(content).map { it.groupValues[1].trim() }.distinct().toList()
}

/**
 * Replaces matching variables inside string with user inputs safely.
 */
fun interpolateVariables(content: String, variables: Map<String, String>): String {
    var result = content
    variables.forEach { (name, value) ->
        val safeValue = if (value.isBlank()) "{$name}" else value
        result = result.replace("{$name}", safeValue)
    }
    return result
}

/**
 * Dialog enabling custom sorting/manual draggable sequencing of categories.
 */
@Composable
fun CategoryReorderDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaveOrder: (List<Category>) -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit
) {
    val reorderedList = remember { mutableStateListOf<Category>().apply { addAll(categories) } }

    LaunchedEffect(categories) {
        reorderedList.clear()
        reorderedList.addAll(categories)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxHeight(0.75f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sort & Reorder Categories ↕️",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Dialogue")
                    }
                }

                Text(
                    text = "Set any category's absolute list position directly from the dropdown, click up/down arrow buttons, or long-press and drag items manually up or down to restructure category display sequence.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(reorderedList, key = { _, category -> category.id }) { index, category ->
                        var isDragging by remember { mutableStateOf(false) }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isDragging) 2.dp else 1.dp,
                                color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDragging) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Custom Drag handle that triggers drag
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Drag Handle",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .pointerInput(index) {
                                                detectDragGestures(
                                                    onDragStart = { isDragging = true },
                                                    onDragEnd = { isDragging = false },
                                                    onDragCancel = { isDragging = false },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val yOffset = dragAmount.y
                                                        if (yOffset < -25f && index > 0) {
                                                            val item = reorderedList.removeAt(index)
                                                            reorderedList.add(index - 1, item)
                                                        } else if (yOffset > 25f && index < reorderedList.size - 1) {
                                                            val item = reorderedList.removeAt(index)
                                                            reorderedList.add(index + 1, item)
                                                        }
                                                    }
                                                )
                                            }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Number Badge
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (index + 1).toString(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Icon and Name
                                    Icon(
                                        imageVector = getIconForName(category.iconName),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = category.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Direct absolute index position setter dropdown
                                    var showNumMenu by remember { mutableStateOf(false) }
                                    Box {
                                        TextButton(
                                            onClick = { showNumMenu = true },
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                text = "Pos: ${index + 1}", 
                                                fontSize = 11.sp, 
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showNumMenu,
                                            onDismissRequest = { showNumMenu = false }
                                        ) {
                                            reorderedList.indices.forEach { targetIdx ->
                                                DropdownMenuItem(
                                                    text = { Text("Move to #${targetIdx + 1}", fontSize = 12.sp) },
                                                    onClick = {
                                                        showNumMenu = false
                                                        if (targetIdx != index) {
                                                            val item = reorderedList.removeAt(index)
                                                            reorderedList.add(targetIdx, item)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Up option
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val prev = reorderedList[index - 1]
                                                reorderedList[index - 1] = category
                                                reorderedList[index] = prev
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Down option
                                    IconButton(
                                        onClick = {
                                            if (index < reorderedList.size - 1) {
                                                val next = reorderedList[index + 1]
                                                reorderedList[index + 1] = category
                                                reorderedList[index] = next
                                            }
                                        },
                                        enabled = index < reorderedList.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Dropdown options menu for Edit / Delete Category
                                    var showMoreMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMoreMenu = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "More Options",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMoreMenu,
                                            onDismissRequest = { showMoreMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Category ✏️") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    showMoreMenu = false
                                                    onEditCategory(category)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Category 🗑️", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    showMoreMenu = false
                                                    onDeleteCategory(category)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val savedList = reorderedList.mapIndexed { i, cat ->
                                cat.copy(orderIndex = i + 1)
                            }
                            onSaveOrder(savedList)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Sequence", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

object GDriveIcons {
    val Cloud: ImageVector by lazy {
        ImageVector.Builder(
            name = "Cloud",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            strokeLineWidth = 0f
        ) {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
            horizontalLineTo(19f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
            curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
            close()
        }.build()
    }

    val CloudQueue: ImageVector by lazy {
        ImageVector.Builder(
            name = "CloudQueue",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            strokeLineWidth = 0f
        ) {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
            horizontalLineTo(19f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
            curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
            close()
            moveTo(19f, 18f)
            horizontalLineTo(6f)
            curveTo(3.79f, 18f, 2f, 16.21f, 2f, 14f)
            curveTo(2f, 11.95f, 3.53f, 10.24f, 5.56f, 10.03f)
            lineTo(6.63f, 9.92f)
            lineTo(7.13f, 8.97f)
            curveTo(8.08f, 7.14f, 9.94f, 6f, 12f, 6f)
            curveTo(14.85f, 6f, 17.27f, 7.86f, 17.82f, 10.51f)
            lineTo(18.09f, 11.79f)
            lineTo(19.38f, 11.88f)
            curveTo(20.89f, 11.99f, 22f, 13.34f, 22f, 15f)
            curveTo(22f, 16.65f, 20.65f, 18f, 19f, 18f)
            close()
        }.build()
    }

    val CloudDone: ImageVector by lazy {
        ImageVector.Builder(
            name = "CloudDone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            strokeLineWidth = 0f
        ) {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
            horizontalLineTo(19f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
            curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
            close()
            moveTo(10f, 17f)
            lineTo(6f, 13f)
            lineTo(7.41f, 11.59f)
            lineTo(10f, 14.17f)
            lineTo(16.59f, 7.58f)
            lineTo(18f, 9f)
            lineTo(10f, 17f)
            close()
        }.build()
    }

    val Logout: ImageVector by lazy {
        ImageVector.Builder(
            name = "Logout",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            strokeLineWidth = 0f
        ) {
            moveTo(17f, 7f)
            lineTo(15.59f, 8.41f)
            lineTo(18.17f, 11f)
            horizontalLineTo(9f)
            verticalLineTo(13f)
            horizontalLineTo(18.17f)
            lineTo(15.59f, 15.58f)
            lineTo(17f, 17f)
            lineTo(22f, 12f)
            lineTo(17f, 7f)
            close()
            moveTo(4f, 5f)
            horizontalLineTo(12f)
            verticalLineTo(3f)
            horizontalLineTo(4f)
            curveTo(2.9f, 3f, 2f, 3.9f, 2f, 5f)
            verticalLineTo(19f)
            curveTo(2f, 20.1f, 2.9f, 21f, 4f, 21f)
            horizontalLineTo(12f)
            verticalLineTo(19f)
            horizontalLineTo(4f)
            verticalLineTo(5f)
            close()
        }.build()
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentSlide by remember { mutableIntStateOf(0) }
    val slides = listOf(
        OnboardingSlide(
            title = "Welcome to Replai ⚡",
            description = "Your system-wide quick communication companion. Save template answers and reply to messages in seconds under any app!",
            icon = Icons.Default.Email
        ),
        OnboardingSlide(
            title = "Placeholders & Variables 💡",
            description = "Insert variables like {name} or {amount} in your templates. When copying, Replai automatically prompts you to personalize them!",
            icon = Icons.Default.Settings
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = slides[currentSlide].icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = slides[currentSlide].title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = slides[currentSlide].description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))
            // Indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slides.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentSlide) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentSlide) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentSlide > 0) {
                    TextButton(onClick = { currentSlide-- }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
                Button(
                    onClick = {
                        if (currentSlide < slides.lastIndex) {
                            currentSlide++
                        } else {
                            onComplete()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (currentSlide == slides.lastIndex) "Get Started 🚀" else "Next")
                }
            }
        }
    }
}

data class OnboardingSlide(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun AdmobBanner(isProMode: Boolean) {
    if (isProMode) return

    val context = LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        factory = { ctx ->
            com.google.android.gms.ads.AdView(ctx).apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                // Test banner ad ID
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
        }
    )
}

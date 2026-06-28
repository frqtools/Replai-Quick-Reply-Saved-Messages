package com.frqtools.replai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import com.frqtools.replai.ui.theme.MyApplicationTheme
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.frqtools.replai.MainActivity
import com.frqtools.replai.data.AppDatabase
import com.frqtools.replai.data.Category
import com.frqtools.replai.data.Reply
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class FloatingBubbleService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var bubbleView: ComposeView? = null
    private var overlayView: android.view.View? = null

    private var bubbleParams = WindowManager.LayoutParams()
    private var overlayParams = WindowManager.LayoutParams()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    // Lifecycle Service Boilerplate
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var categories = mutableStateListOf<Category>()
    private var replies = mutableStateListOf<Reply>()
    private var selectedCategoryId = mutableStateOf<Long?>(null)
    private var activeVariableReply = mutableStateOf<Reply?>(null)

    companion object {
        private const val CHANNEL_ID = "FloatingBubbleChannel"
        private const val NOTIFICATION_ID = 5001
        const val ACTION_STOP = "STOP_BUBBLE_SERVICE"
        val isRunningState = androidx.compose.runtime.mutableStateOf(false)
        var isRunning: Boolean
            get() = isRunningState.value
            set(value) {
                isRunningState.value = value
            }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        loadDatabaseData()
        showFloatingBubble()
    }

    private fun loadDatabaseData() {
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Collect categories reactively
            launch {
                db.categoryDao().getAllCategories().collect { cats ->
                    categories.clear()
                    categories.addAll(cats)
                    if (selectedCategoryId.value == null && cats.isNotEmpty()) {
                        selectedCategoryId.value = cats.first().id
                    } else if (cats.isNotEmpty() && cats.none { it.id == selectedCategoryId.value }) {
                        selectedCategoryId.value = cats.first().id
                    }
                }
            }

            // Collect replies reactively
            launch {
                db.replyDao().getAllReplies().collect { reps ->
                    replies.clear()
                    replies.addAll(reps)
                }
            }
        }
    }

    private fun showFloatingBubble() {
        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)

            setContent {
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = remember { context.getSharedPreferences("replai_settings", Context.MODE_PRIVATE) }
                val themeMode = remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
                val isDark = when (themeMode.value) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }

                MyApplicationTheme(darkTheme = isDark) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown()
                                        var hasMoved = false
                                        var dragDistance = 0f
                                        var lastPosition = down.position
                                        do {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull()
                                            if (change != null && change.pressed) {
                                                val currentPosition = change.position
                                                val dragAmount = currentPosition - lastPosition
                                                dragDistance += kotlin.math.sqrt(dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y)
                                                if (dragDistance > 8f) {
                                                    hasMoved = true
                                                }
                                                if (hasMoved) {
                                                    bubbleParams.x += dragAmount.x.toInt()
                                                    bubbleParams.y += dragAmount.y.toInt()
                                                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                                                    change.consume()
                                                }
                                                lastPosition = currentPosition
                                            }
                                        } while (event.changes.any { it.pressed })

                                        if (!hasMoved) {
                                            toggleOverlay()
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Replai Bubble",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun toggleOverlay() {
        if (overlayView != null) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    private fun showOverlay() {
        val frameLayout = object : android.widget.FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        hideOverlay()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)

            setContent {
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = remember { context.getSharedPreferences("replai_settings", Context.MODE_PRIVATE) }
                val themeMode = remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
                val isDark = when (themeMode.value) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }

                MyApplicationTheme(darkTheme = isDark) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                hideOverlay()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(380.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Intercept clicks inside the surface so they do not close the overlay
                                },
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp
                        ) {
                            val activeReply = activeVariableReply.value
                            if (activeReply != null) {
                                val variableList = remember(activeReply.content) { extractVariables(activeReply.content) }
                                val variableMap = remember { mutableStateMapOf<String, String>() }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Personalize Reply ⚡",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = activeReply.title,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { activeVariableReply.value = null }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(variableList) { variable ->
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = "{$variable}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                                OutlinedTextField(
                                                    value = variableMap[variable] ?: "",
                                                    onValueChange = { variableMap[variable] = it },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 2.dp),
                                                    placeholder = { Text("E.g., John, $49...", fontSize = 12.sp) },
                                                    singleLine = true,
                                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                                                )
                                            }
                                        }

                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val interpolatedPreview = remember(variableMap.entries.toList(), activeReply.content) {
                                                interpolateVariables(activeReply.content, variableMap)
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
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { activeVariableReply.value = null }) {
                                            Text("Cancel", fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val finalMsg = interpolateVariables(activeReply.content, variableMap)
                                                copyToClipboardAndCloseWithContent(activeReply, finalMsg)
                                            }
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy & Close", fontSize = 13.sp)
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Replai Quick Access",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(onClick = { hideOverlay() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                    }

                                    // Categories List (excluding AI Prompts categories)
                                    val visibleCategories = categories.filter { !it.isForAi }
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(visibleCategories) { category ->
                                            val isSelected = selectedCategoryId.value == category.id
                                            val color = try {
                                                Color(android.graphics.Color.parseColor(category.colorHex))
                                            } catch (e: Exception) {
                                                MaterialTheme.colorScheme.primary
                                            }
                                            val labelColor = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                                            AssistChip(
                                                onClick = { selectedCategoryId.value = category.id },
                                                label = { Text(category.name, fontSize = 12.sp) },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent,
                                                    labelColor = labelColor
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Replies filtered by selected category, excluding AI prompts, sorted with pinned first
                                    val filteredList = replies.filter {
                                        it.categoryId == selectedCategoryId.value && !it.isAiPrompt
                                    }.sortedWith(compareByDescending<Reply> { it.isPinned }.thenByDescending { it.usageCount })

                                    if (filteredList.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1.0f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No templates in this category.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1.0f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(filteredList) { reply ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val vars = extractVariables(reply.content)
                                                            if (vars.isNotEmpty()) {
                                                                activeVariableReply.value = reply
                                                            } else {
                                                                copyToClipboardAndClose(reply)
                                                            }
                                                        },
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = reply.title,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            if (reply.isPinned) {
                                                                Icon(
                                                                    Icons.Default.Star,
                                                                    contentDescription = "Pinned",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = reply.content,
                                                            fontSize = 11.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        frameLayout.addView(composeView)
        overlayView = frameLayout

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            dimAmount = 0.4f
            gravity = Gravity.CENTER
        }

        windowManager.addView(overlayView, overlayParams)
    }

    private fun hideOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }

    private fun copyToClipboardAndClose(reply: Reply) {
        scope.launch(Dispatchers.IO) {
            // Update last used at and usage count in database
            val db = AppDatabase.getDatabase(applicationContext)
            db.replyDao().updateReply(
                reply.copy(
                    usageCount = reply.usageCount + 1,
                    lastUsedAt = System.currentTimeMillis()
                )
            )

            withContext(Dispatchers.Main) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Replai Template", reply.content)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(applicationContext, "Copied template! 📋", Toast.LENGTH_SHORT).show()
                hideOverlay()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Replai Overlay Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background notification channel for quick-access templates bubble"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Replai Floating Access")
            .setContentText("Tap to open app, or tap Stop to close the floating bubble.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun extractVariables(content: String): List<String> {
        val variableRegex = """\{([^{}]+)\}""".toRegex()
        return variableRegex.findAll(content).map { it.groupValues[1].trim() }.distinct().toList()
    }

    private fun interpolateVariables(content: String, variables: Map<String, String>): String {
        var result = content
        variables.forEach { (name, value) ->
            val safeValue = if (value.isBlank()) "{$name}" else value
            result = result.replace("{$name}", safeValue)
        }
        return result
    }

    private fun copyToClipboardAndCloseWithContent(reply: Reply, finalContent: String) {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            db.replyDao().updateReply(
                reply.copy(
                    usageCount = reply.usageCount + 1,
                    lastUsedAt = System.currentTimeMillis()
                )
            )

            withContext(Dispatchers.Main) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Replai Template", finalContent)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(applicationContext, "Copied template! 📋", Toast.LENGTH_SHORT).show()
                activeVariableReply.value = null
                hideOverlay()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        if (bubbleView != null) {
            windowManager.removeView(bubbleView)
            bubbleView = null
        }
        hideOverlay()
    }
}

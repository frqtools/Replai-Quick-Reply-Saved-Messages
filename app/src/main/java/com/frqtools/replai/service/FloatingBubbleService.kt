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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlinx.coroutines.flow.first

class FloatingBubbleService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var bubbleView: ComposeView? = null
    private var overlayView: ComposeView? = null

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

    companion object {
        private const val CHANNEL_ID = "FloatingBubbleChannel"
        private const val NOTIFICATION_ID = 5001
        const val ACTION_STOP = "STOP_BUBBLE_SERVICE"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        loadDatabaseData()
        showFloatingBubble()
    }

    private fun loadDatabaseData() {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val cats = db.categoryDao().getAllCategories().first()
            val reps = db.replyDao().getAllReplies().first()
            withContext(Dispatchers.Main) {
                categories.clear()
                categories.addAll(cats)
                if (cats.isNotEmpty()) {
                    selectedCategoryId.value = cats.first().id
                }
                replies.clear()
                replies.addAll(reps)
            }
        }
    }

    private fun showFloatingBubble() {
        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)

            setContent {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6200EE))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { },
                                onDragEnd = { },
                                onDragCancel = { },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    bubbleParams.x += dragAmount.x.toInt()
                                    bubbleParams.y += dragAmount.y.toInt()
                                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                                }
                            )
                        }
                        .clickable {
                            toggleOverlay()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Replai Bubble",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
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
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)

            setContent {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(380.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
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

                        // Categories List
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(categories) { category ->
                                val isSelected = selectedCategoryId.value == category.id
                                val color = try {
                                    Color(android.graphics.Color.parseColor(category.colorHex))
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                AssistChip(
                                    onClick = { selectedCategoryId.value = category.id },
                                    label = { Text(category.name, fontSize = 12.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent,
                                        labelColor = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Replies filtered by selected category
                        val filteredList = replies.filter { it.categoryId == selectedCategoryId.value }

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
                                                copyToClipboardAndClose(reply)
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

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
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

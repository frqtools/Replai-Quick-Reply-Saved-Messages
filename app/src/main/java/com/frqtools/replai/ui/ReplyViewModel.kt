package com.frqtools.replai.ui

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import android.content.ContentValues
import android.net.Uri
import android.content.ContentUris
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frqtools.replai.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ReplyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ReplyRepository(database.categoryDao(), database.replyDao())
    private val prefs = application.getSharedPreferences("replai_settings", Context.MODE_PRIVATE)

    // Persistent Settings
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _categorySortOrder = MutableStateFlow(prefs.getString("category_sort_order", "order") ?: "order")
    val categorySortOrder: StateFlow<String> = _categorySortOrder.asStateFlow()

    private val _replySortOrder = MutableStateFlow(prefs.getString("reply_sort_order", "usage") ?: "usage")
    val replySortOrder: StateFlow<String> = _replySortOrder.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _isProMode = MutableStateFlow(true)
    val isProMode: StateFlow<Boolean> = _isProMode.asStateFlow()

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setCategorySortOrder(order: String) {
        _categorySortOrder.value = order
        prefs.edit().putString("category_sort_order", order).apply()
    }

    fun setReplySortOrder(order: String) {
        _replySortOrder.value = order
        prefs.edit().putString("reply_sort_order", order).apply()
    }

    // Modern WhatsApp-style Local Auto-Backup System States
    private val _showRestorePrompt = MutableStateFlow(false)
    val showRestorePrompt: StateFlow<Boolean> = _showRestorePrompt.asStateFlow()

    private val _backupDateString = MutableStateFlow("")
    val backupDateString: StateFlow<String> = _backupDateString.asStateFlow()

    private val _detectedBackupContent = MutableStateFlow<String?>(null)
    val detectedBackupContent: StateFlow<String?> = _detectedBackupContent.asStateFlow()

    private fun hasWritePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun getBackupUri(context: Context): Uri? {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            return null
        }

        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("replai_auto_backup.json")

        try {
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val relativePath = cursor.getString(pathColumn)
                    if (relativePath != null && relativePath.contains("Documents/ReplaiBackup", ignoreCase = true)) {
                        return ContentUris.withAppendedId(collection, id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Backup", "Error querying MediaStore for backup file", e)
        }
        return null
    }

    private fun saveAutoBackup(context: Context, jsonString: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val existingUri = getBackupUri(context)
            if (existingUri != null) {
                try {
                    resolver.openOutputStream(existingUri, "wt")?.use { os ->
                        os.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    return true
                } catch (e: Exception) {
                    Log.e("Backup", "Error overwriting existing MediaStore backup", e)
                }
            }

            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "replai_auto_backup.json")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/ReplaiBackup")
                }
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val newUri = resolver.insert(collection, contentValues)
                if (newUri != null) {
                    resolver.openOutputStream(newUri)?.use { os ->
                        os.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    return true
                }
            } catch (e: Exception) {
                Log.e("Backup", "Error inserting new MediaStore backup", e)
            }
        } else {
            if (!hasWritePermission(context)) {
                Log.w("Backup", "Write external storage permission not granted on API < 29, skipping backup.")
                return false
            }
            try {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val backupDir = File(documentsDir, "ReplaiBackup")
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }
                val backupFile = File(backupDir, "replai_auto_backup.json")
                backupFile.writeText(jsonString)
                return true
            } catch (e: Exception) {
                Log.e("Backup", "Error writing direct file backup on older SDK", e)
            }
        }
        return false
    }

    private fun readAutoBackup(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = getBackupUri(context) ?: return null
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    return inputStream.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                Log.e("Backup", "Error reading MediaStore backup", e)
            }
        } else {
            try {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val backupFile = File(File(documentsDir, "ReplaiBackup"), "replai_auto_backup.json")
                if (backupFile.exists() && backupFile.canRead()) {
                    return backupFile.readText()
                }
            } catch (e: Exception) {
                Log.e("Backup", "Error reading direct file backup on older SDK", e)
            }
        }
        return null
    }

    private suspend fun triggerAutoBackup() = withContext(Dispatchers.IO) {
        try {
            val categoriesList = database.categoryDao().getAllCategories().first()
            val repliesList = database.replyDao().getAllReplies().first()

            val root = JSONObject()
            root.put("version", 1)
            root.put("exportedAt", System.currentTimeMillis())

            val categoriesArray = JSONArray()
            categoriesList.forEach { category ->
                val categoryObj = JSONObject()
                categoryObj.put("id", category.id)
                categoryObj.put("name", category.name)
                categoryObj.put("iconName", category.iconName)
                categoryObj.put("orderIndex", category.orderIndex)
                categoryObj.put("isForAi", category.isForAi)
                categoryObj.put("colorHex", category.colorHex)
                categoriesArray.put(categoryObj)
            }
            root.put("categories", categoriesArray)

            val repliesArray = JSONArray()
            repliesList.forEach { reply ->
                val replyObj = JSONObject()
                replyObj.put("id", reply.id)
                replyObj.put("categoryId", reply.categoryId)
                replyObj.put("title", reply.title)
                replyObj.put("content", reply.content)
                replyObj.put("usageCount", reply.usageCount)
                replyObj.put("isAiPrompt", reply.isAiPrompt)
                replyObj.put("isPinned", reply.isPinned)
                replyObj.put("createdAt", reply.createdAt)
                replyObj.put("lastUsedAt", reply.lastUsedAt)
                repliesArray.put(replyObj)
            }
            root.put("replies", repliesArray)

            val jsonString = root.toString(2)
            val context = getApplication<Application>()
            saveAutoBackup(context, jsonString)
        } catch (e: Exception) {
            Log.e("AutoBackup", "Failed to write rolling backup", e)
        }
    }

    fun checkForBackupOnFirstLaunch(context: Context) {
        if (prefs.getBoolean("backup_restore_checked", false)) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val catCount = database.categoryDao().getCategoryCount()
                val repCount = database.replyDao().getReplyCount()

                if (catCount == 0 && repCount == 0) {
                    val jsonString = readAutoBackup(context)
                    if (jsonString != null) {
                        _detectedBackupContent.value = jsonString
                        val root = JSONObject(jsonString)
                        val timestamp = root.optLong("exportedAt", 0L)
                        if (timestamp > 0L) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            _backupDateString.value = sdf.format(java.util.Date(timestamp))
                            _showRestorePrompt.value = true
                            return@launch
                        }
                    }
                    // No backup exists, and database is empty -> seed default templates
                    repository.seedDatabaseIfNeeded()
                }
                // If database has data or no backup found, check is complete.
                prefs.edit().putBoolean("backup_restore_checked", true).apply()
            } catch (e: Exception) {
                Log.e("FirstLaunchCheck", "Failed to check for backup", e)
                prefs.edit().putBoolean("backup_restore_checked", true).apply()
            }
        }
    }

    fun restoreFromAutoBackup(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = _detectedBackupContent.value ?: readAutoBackup(context)
                if (jsonString == null) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Backup file could not be read. Starting fresh.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    dismissRestorePrompt()
                    return@launch
                }

                val root = JSONObject(jsonString)
                val categoriesArray = root.getJSONArray("categories")
                val repliesArray = root.getJSONArray("replies")

                database.replyDao().deleteAllReplies()
                database.categoryDao().deleteAllCategories()

                for (i in 0 until categoriesArray.length()) {
                    val catObj = categoriesArray.getJSONObject(i)
                    val catId = catObj.getLong("id")
                    val catName = catObj.getString("name")
                    val catIcon = catObj.optString("iconName", "folder")
                    val catOrder = catObj.optInt("orderIndex", 0)
                    val catIsForAi = catObj.optBoolean("isForAi", false)
                    val catColorHex = catObj.optString("colorHex", "#6200EE")

                    database.categoryDao().insertCategory(
                        Category(
                            id = catId,
                            name = catName,
                            iconName = catIcon,
                            orderIndex = catOrder,
                            isForAi = catIsForAi,
                            colorHex = catColorHex
                        )
                    )
                }

                for (i in 0 until repliesArray.length()) {
                    val replyObj = repliesArray.getJSONObject(i)
                    val rId = replyObj.getLong("id")
                    val rCategoryId = replyObj.getLong("categoryId")
                    val rTitle = replyObj.getString("title")
                    val rContent = replyObj.getString("content")
                    val rCount = replyObj.optInt("usageCount", 0)
                    val rIsAi = replyObj.optBoolean("isAiPrompt", false)
                    val rIsPinned = replyObj.optBoolean("isPinned", false)
                    val rCreatedAt = replyObj.optLong("createdAt", System.currentTimeMillis())
                    val rLastUsedAt = replyObj.optLong("lastUsedAt", 0L)

                    database.replyDao().insertReply(
                        Reply(
                            id = rId,
                            categoryId = rCategoryId,
                            title = rTitle,
                            content = rContent,
                            usageCount = rCount,
                            isAiPrompt = rIsAi,
                            isPinned = rIsPinned,
                            createdAt = rCreatedAt,
                            lastUsedAt = rLastUsedAt
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Backup restored! ${repliesArray.length()} templates across ${categoriesArray.length()} categories loaded.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("RestoreBackup", "Error restoring backup", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Backup file could not be read. Starting fresh.", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                dismissRestorePrompt()
            }
        }
    }

    fun dismissRestorePrompt() {
        _showRestorePrompt.value = false
        prefs.edit().putBoolean("backup_restore_checked", true).apply()
        viewModelScope.launch(Dispatchers.IO) {
            val catCount = database.categoryDao().getCategoryCount()
            if (catCount == 0) {
                repository.seedDatabaseIfNeeded()
            }
        }
    }

    fun triggerLocalAutoBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            triggerAutoBackup()
        }
    }

    private fun markLocalUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            triggerAutoBackup()
        }
    }

    // All categories flow, with dynamic sorting applied
    val categories: StateFlow<List<Category>> = repository.allCategories
        .combine(_categorySortOrder) { list, sort ->
            when (sort) {
                "alphabetical" -> list.sortedBy { it.name.lowercase() }
                "alphabetical_desc" -> list.sortedByDescending { it.name.lowercase() }
                else -> list.sortedBy { it.orderIndex }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All replies flow
    val allReplies: StateFlow<List<Reply>> = repository.allReplies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filtered replies flow based on search query, category names and reply sort mode (with pinned on top)
    val filteredReplies: StateFlow<List<Reply>> = combine(allReplies, categories, _searchQuery, _replySortOrder) { replies, cats, query, sort ->
        val filtered = if (query.isBlank()) {
            replies
        } else {
            replies.filter { reply ->
                reply.title.contains(query, ignoreCase = true) ||
                        reply.content.contains(query, ignoreCase = true) ||
                        cats.find { it.id == reply.categoryId }?.name?.contains(query, ignoreCase = true) == true
            }
        }
        val sorted = when (sort) {
            "alphabetical" -> filtered.sortedBy { it.title.lowercase() }
            "usage" -> filtered.sortedByDescending { it.usageCount }
            else -> filtered.sortedByDescending { it.id } // "recent" sorts by ID descending
        }
        // Always place pinned replies first!
        sorted.sortedByDescending { it.isPinned }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePinReply(reply: Reply) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = reply.copy(isPinned = !reply.isPinned)
            repository.updateReply(updated)
            val msg = if (updated.isPinned) "Pinned '${reply.title}' to top" else "Unpinned '${reply.title}'"
            _toastMessage.emit(msg)
            markLocalUpdate()
        }
    }

    fun duplicateReply(reply: Reply) {
        viewModelScope.launch(Dispatchers.IO) {
            val duplicated = reply.copy(
                id = 0, // auto-generate new ID
                title = "${reply.title} (Copy)",
                createdAt = System.currentTimeMillis(),
                usageCount = 0,
                isPinned = false
            )
            repository.insertReply(duplicated)
            _toastMessage.emit("Duplicated template '${reply.title}'")
            markLocalUpdate()
        }
    }

    fun updateCategoryColor(category: Category, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = category.copy(colorHex = colorHex)
            repository.updateCategory(updated)
            _toastMessage.emit("Updated category color")
            markLocalUpdate()
        }
    }

    // UI Toast or Event notifications
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val alreadyChecked = prefs.getBoolean("backup_restore_checked", false)
            val catCount = database.categoryDao().getCategoryCount()
            if (!alreadyChecked && catCount == 0) {
                // Backup check will happen in checkForBackupOnFirstLaunch — don't seed yet
            } else {
                repository.seedDatabaseIfNeeded()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Category actions
    fun addCategory(name: String, iconName: String, isForAi: Boolean = false, colorHex: String = "#6200EE") {
        viewModelScope.launch(Dispatchers.IO) {
            val maxOrder = categories.value.maxOfOrNull { it.orderIndex } ?: 0
            repository.insertCategory(Category(name = name, iconName = iconName, orderIndex = maxOrder + 1, isForAi = isForAi, colorHex = colorHex))
            _toastMessage.emit("Category '$name' created")
            markLocalUpdate()
        }
    }

    fun updateCategory(category: Category, newName: String, newIcon: String, isForAi: Boolean, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(category.copy(name = newName, iconName = newIcon, isForAi = isForAi, colorHex = colorHex))
            _toastMessage.emit("Category updated")
            markLocalUpdate()
        }
    }

    fun updateCategoriesOrder(reordered: List<Category>) {
        viewModelScope.launch(Dispatchers.IO) {
            reordered.forEach { category ->
                repository.updateCategory(category)
            }
            _toastMessage.emit("Category order updated")
            markLocalUpdate()
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(category)
            _toastMessage.emit("Category '${category.name}' deleted")
            markLocalUpdate()
        }
    }

    // Reply actions
    fun checkForDuplicate(title: String, content: String): Boolean {
        return allReplies.value.any {
            it.title.trim().equals(title.trim(), ignoreCase = true) &&
                    it.content.trim().equals(content.trim(), ignoreCase = true)
        }
    }

    fun addReply(categoryId: Long, title: String, content: String, isAiPrompt: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertReply(
                Reply(
                    categoryId = categoryId,
                    title = title,
                    content = content,
                    isAiPrompt = isAiPrompt
                )
            )
            _toastMessage.emit("Reply '$title' saved")
            markLocalUpdate()
        }
    }

    fun updateReply(reply: Reply, title: String, content: String, categoryId: Long, isAiPrompt: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateReply(
                reply.copy(
                    title = title,
                    content = content,
                    categoryId = categoryId,
                    isAiPrompt = isAiPrompt
                )
            )
            _toastMessage.emit("Reply '$title' updated")
            markLocalUpdate()
        }
    }

    fun deleteReply(reply: Reply) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReply(reply)
            _toastMessage.emit("Reply '${reply.title}' deleted")
            markLocalUpdate()
        }
    }

    fun deleteReplies(replyIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                replyIds.forEach { id ->
                    val reply = allReplies.value.firstOrNull { it.id == id }
                    if (reply != null) {
                        repository.deleteReply(reply)
                    }
                }
                _toastMessage.emit("Deleted ${replyIds.size} templates")
                markLocalUpdate()
            } catch (e: Exception) {
                _toastMessage.emit("Failed to delete templates")
            }
        }
    }

    fun incrementUsage(replyId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val reply = allReplies.value.find { it.id == replyId }
            if (reply != null) {
                repository.updateReply(
                    reply.copy(
                        usageCount = reply.usageCount + 1,
                        lastUsedAt = System.currentTimeMillis()
                    )
                )
            } else {
                repository.incrementUsageCount(replyId)
            }
            markLocalUpdate()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    fun bulkImportReplies(
        categoryId: Long,
        inputText: String,
        isAiPrompt: Boolean,
        onComplete: (Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            val normalizedInput = inputText.replace("\r\n", "\n").trim()
            if (normalizedInput.isEmpty()) {
                withContext(Dispatchers.Main) { onComplete(0) }
                return@launch
            }

            val lines = normalizedInput.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val itemsToInsert = mutableListOf<Reply>()

            // Detect if input has Excel/TSV format (tabs on any line)
            val hasTabs = normalizedInput.contains("\t")
            // Detect if pipes are used as delimiters
            val hasPipes = normalizedInput.contains("|")
            // Detect if commas are present
            val hasCommas = normalizedInput.lines().any { it.contains(",") }

            if (hasTabs) {
                // Excel copy-paste: tab-delimited
                for (line in lines) {
                    val parts = line.split("\t", limit = 2)
                    if (parts.size == 2) {
                        val title = parts[0].trim().removeSurrounding("\"")
                        val content = parts[1].trim().removeSurrounding("\"")
                        if (title.isNotEmpty() && content.isNotEmpty()) {
                            itemsToInsert.add(
                                Reply(categoryId = categoryId, title = title, content = content, isAiPrompt = isAiPrompt)
                            )
                        }
                    } else if (parts.isNotEmpty()) {
                        val text = parts[0].trim().removeSurrounding("\"")
                        if (text.isNotEmpty()) {
                            val words = text.split(Regex("\\s+"))
                            val title = words.take(3).joinToString(" ")
                            itemsToInsert.add(
                                Reply(categoryId = categoryId, title = title, content = text, isAiPrompt = isAiPrompt)
                            )
                        }
                    }
                }
            } else if (hasPipes) {
                // Pipe delimited format
                for (line in lines) {
                    val parts = line.split("|", limit = 2)
                    if (parts.size == 2) {
                        val title = parts[0].trim()
                        val content = parts[1].trim()
                        if (title.isNotEmpty() && content.isNotEmpty()) {
                            itemsToInsert.add(
                                Reply(categoryId = categoryId, title = title, content = content, isAiPrompt = isAiPrompt)
                            )
                        }
                    } else if (parts.isNotEmpty()) {
                        val text = parts[0].trim()
                        if (text.isNotEmpty()) {
                            val words = text.split(Regex("\\s+"))
                            val title = words.take(3).joinToString(" ")
                            itemsToInsert.add(
                                Reply(categoryId = categoryId, title = title, content = text, isAiPrompt = isAiPrompt)
                            )
                        }
                    }
                }
            } else if (hasCommas) {
                // CSV formatted
                for (line in lines) {
                    val parts = parseCsvLine(line)
                    if (parts.size >= 2) {
                        val title = parts[0].removeSurrounding("\"")
                        val content = parts[1].removeSurrounding("\"")
                        if (title.isNotEmpty() && content.isNotEmpty()) {
                            itemsToInsert.add(
                                Reply(categoryId = categoryId, title = title, content = content, isAiPrompt = isAiPrompt)
                            )
                        }
                    } else if (parts.isNotEmpty()) {
                        val text = parts[0].removeSurrounding("\"")
                        if (text.isNotEmpty()) {
                            val words = text.split(Regex("\\s+"))
                            val title = words.take(3).joinToString(" ")
                            itemsToInsert.add(
                                Reply(categoryId = categoryId, title = title, content = text, isAiPrompt = isAiPrompt)
                            )
                        }
                    }
                }
            } else {
                // Separated by double-newline or --- or fallback
                val separator = if (normalizedInput.contains("---")) "---" else "\n\n"
                val blocks = normalizedInput.split(separator)
                for (block in blocks) {
                    val blockLines = block.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (blockLines.isEmpty()) continue
                    
                    val title = blockLines.first()
                    val content = blockLines.drop(1).joinToString("\n")
                    
                    if (title.isNotEmpty()) {
                        itemsToInsert.add(
                            Reply(
                                categoryId = categoryId,
                                title = title,
                                content = if (content.isNotEmpty()) content else title,
                                isAiPrompt = isAiPrompt
                            )
                        )
                    }
                }
            }

            for (reply in itemsToInsert) {
                repository.insertReply(reply)
                count++
            }

            withContext(Dispatchers.Main) {
                onComplete(count)
            }
            if (count > 0) {
                _toastMessage.emit("Successfully imported $count templates! 🚀")
                markLocalUpdate()
            } else {
                _toastMessage.emit("No valid templates found to import")
            }
        }
    }

    // JSON export/backup helper
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        try {
            val categoriesList = categories.first()
            val repliesList = allReplies.first()

            val root = JSONObject()
            root.put("app", "Replai")
            root.put("version", 1)
            root.put("exportedAt", System.currentTimeMillis())

            val categoriesArray = JSONArray()
            categoriesList.forEach { category ->
                val categoryObj = JSONObject()
                categoryObj.put("name", category.name)
                categoryObj.put("iconName", category.iconName)
                categoryObj.put("orderIndex", category.orderIndex)
                categoryObj.put("colorHex", category.colorHex)

                val repliesArray = JSONArray()
                val matchedReplies = repliesList.filter { it.categoryId == category.id }
                matchedReplies.forEach { reply ->
                    val replyObj = JSONObject()
                    replyObj.put("title", reply.title)
                    replyObj.put("content", reply.content)
                    replyObj.put("usageCount", reply.usageCount)
                    replyObj.put("isAiPrompt", reply.isAiPrompt)
                    replyObj.put("isPinned", reply.isPinned)
                    replyObj.put("lastUsedAt", reply.lastUsedAt)
                    repliesArray.put(replyObj)
                }

                categoryObj.put("replies", repliesArray)
                categoriesArray.put(categoryObj)
            }

            root.put("data", categoriesArray)
            return@withContext root.toString(2)
        } catch (e: Exception) {
            Log.e("ReplyViewModel", "Export failed", e)
            return@withContext ""
        }
    }

    // JSON import/restore helper
    suspend fun importFromJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("app") || root.getString("app") != "Replai") {
                _toastMessage.emit("Invalid backup: App identifier does not match.")
                return@withContext false
            }

            val dataArray = root.getJSONArray("data")
            for (i in 0 until dataArray.length()) {
                val catObj = dataArray.getJSONObject(i)
                val catName = catObj.getString("name")
                val catIcon = catObj.optString("iconName", "folder")
                val catOrder = catObj.optInt("orderIndex", 0)
                val catIsForAi = catObj.optBoolean("isForAi", false)
                val catColorHex = catObj.optString("colorHex", "#6200EE")

                // Check if category already matches name
                var category = database.categoryDao().getCategoryByName(catName)
                val categoryId = if (category != null) {
                    category.id
                } else {
                    database.categoryDao().insertCategory(
                        Category(
                            name = catName,
                            iconName = catIcon,
                            orderIndex = catOrder,
                            isForAi = catIsForAi,
                            colorHex = catColorHex
                        )
                    )
                }

                val repliesArray = catObj.getJSONArray("replies")
                for (j in 0 until repliesArray.length()) {
                    val replyObj = repliesArray.getJSONObject(j)
                    val rTitle = replyObj.getString("title")
                    val rContent = replyObj.getString("content")
                    val rCount = replyObj.optInt("usageCount", 0)
                    val rIsAi = replyObj.optBoolean("isAiPrompt", false)
                    val rIsPinned = replyObj.optBoolean("isPinned", false)
                    val rLastUsedAt = replyObj.optLong("lastUsedAt", 0L)

                    // Simply insert all replies (can have duplicates or duplicates checked as needed)
                    database.replyDao().insertReply(
                        Reply(
                            categoryId = categoryId,
                            title = rTitle,
                            content = rContent,
                            usageCount = rCount,
                            isAiPrompt = rIsAi,
                            isPinned = rIsPinned,
                            lastUsedAt = rLastUsedAt
                        )
                    )
                }
            }

            _toastMessage.emit("Backup imported successfully!")
            return@withContext true
        } catch (e: Exception) {
            Log.e("ReplyViewModel", "Import failed", e)
            _toastMessage.emit("Failed to import backup: invalid JSON format.")
            return@withContext false
        }
    }



    private fun parseFullCsv(body: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = body.length

        while (i < len) {
            val c = body[i]
            if (c == '"') {
                if (i + 1 < len && body[i + 1] == '"') {
                    currentField.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                currentRecord.add(currentField.toString())
                currentField.setLength(0)
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < len && body[i + 1] == '\n') {
                    i++
                }
                currentRecord.add(currentField.toString())
                currentField.setLength(0)
                if (currentRecord.isNotEmpty() && currentRecord.any { it.isNotBlank() }) {
                    result.add(currentRecord)
                }
                currentRecord = mutableListOf()
            } else {
                currentField.append(c)
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(currentField.toString())
            if (currentRecord.any { it.isNotBlank() }) {
                result.add(currentRecord)
            }
        }
        return result
    }
}

package com.frqtools.replai.ui

import android.app.Application
import android.content.Context
import android.util.Log
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

    private val _isProMode = MutableStateFlow(prefs.getBoolean("is_pro_mode", false))
    val isProMode: StateFlow<Boolean> = _isProMode.asStateFlow()

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }

    fun setProMode(enabled: Boolean) {
        _isProMode.value = enabled
        prefs.edit().putBoolean("is_pro_mode", enabled).apply()
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

    // Sheet connection states
    val isSyncing = MutableStateFlow(false)

    // Data structure for Sheet row / entry
    data class SheetRow(
        val category: String,
        val title: String,
        val content: String,
        val isAi: Boolean
    )

    // Remote URL / Google Sheets Sync State Variables
    private val _remoteUrlSyncUrl = MutableStateFlow(prefs.getString("remote_url_sync_url", "") ?: "")
    val remoteUrlSyncUrl: StateFlow<String> = _remoteUrlSyncUrl.asStateFlow()

    private val _remoteUrlSyncAppend = MutableStateFlow(prefs.getBoolean("remote_url_sync_append", false))
    val remoteUrlSyncAppend: StateFlow<Boolean> = _remoteUrlSyncAppend.asStateFlow()

    private val _remoteUrlLastSyncTime = MutableStateFlow(prefs.getLong("remote_url_last_sync_time", 0L))
    val remoteUrlLastSyncTime: StateFlow<Long> = _remoteUrlLastSyncTime.asStateFlow()

    private val _remoteUrlSyncStatus = MutableStateFlow(prefs.getString("remote_url_sync_status", "Not Connected") ?: "Not Connected")
    val remoteUrlSyncStatus: StateFlow<String> = _remoteUrlSyncStatus.asStateFlow()

    fun setRemoteUrlSyncSettings(url: String, append: Boolean) {
        _remoteUrlSyncUrl.value = url.trim()
        _remoteUrlSyncAppend.value = append
        prefs.edit()
            .putString("remote_url_sync_url", url.trim())
            .putBoolean("remote_url_sync_append", append)
            .apply()
    }

    fun updateRemoteUrlSyncStatus(status: String) {
        _remoteUrlSyncStatus.value = status
        prefs.edit().putString("remote_url_sync_status", status).apply()
    }

    fun updateRemoteUrlLastSyncTime(timestamp: Long) {
        _remoteUrlLastSyncTime.value = timestamp
        prefs.edit().putLong("remote_url_last_sync_time", timestamp).apply()
    }

    private fun markLocalUpdate() {
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong("gdrive_last_local_update_time", currentTime).apply()
        
        val syncUrl = _remoteUrlSyncUrl.value.trim()
        if (syncUrl.isNotBlank() && syncUrl.contains("script.google.com")) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val localCategories = database.categoryDao().getAllCategories().first()
                    val localReplies = database.replyDao().getAllReplies().first()
                    val rows = localReplies.mapNotNull { reply ->
                        localCategories.find { it.id == reply.categoryId }?.let { category ->
                            SheetRow(
                                category = category.name,
                                title = reply.title,
                                content = reply.content,
                                isAi = reply.isAiPrompt
                            )
                        }
                    }
                    writeRemoteRows(syncUrl, rows)
                } catch (e: Exception) {
                    Log.e("SheetSync", "Auto-sync update failed", e)
                }
            }
        }
    }

    private fun mergeRows(local: List<SheetRow>, remote: List<SheetRow>): List<SheetRow> {
        val merged = mutableListOf<SheetRow>()
        val seenKeys = mutableSetOf<String>()

        // 1. Add remote rows
        for (row in remote) {
            val key = "${row.category.lowercase().trim()}|||${row.title.lowercase().trim()}|||${row.content.lowercase().trim()}"
            if (!seenKeys.contains(key)) {
                seenKeys.add(key)
                merged.add(row)
            }
        }

        // 2. Add local rows that are not in remote
        for (row in local) {
            val key = "${row.category.lowercase().trim()}|||${row.title.lowercase().trim()}|||${row.content.lowercase().trim()}"
            if (!seenKeys.contains(key)) {
                seenKeys.add(key)
                merged.add(row)
            }
        }
        return merged
    }

    private suspend fun fetchRemoteRows(url: String): List<SheetRow> {
        var downloadUrl = url.trim()
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        if (downloadUrl.contains("docs.google.com/spreadsheets")) {
            val regex = "/d/([a-zA-Z0-9-_]+)".toRegex()
            val match = regex.find(downloadUrl)
            val sheetId = match?.groups?.get(1)?.value
            if (sheetId != null) {
                downloadUrl = "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv"
            }
        }

        val request = okhttp3.Request.Builder().url(downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP Error ${response.code}")
            }
            val body = response.body?.string() ?: return emptyList()
            val trimmedBody = body.trim()

            if (trimmedBody.startsWith("[") || trimmedBody.startsWith("{")) {
                val list = mutableListOf<SheetRow>()
                try {
                    val jsonArray = if (trimmedBody.startsWith("{")) {
                        val obj = JSONObject(trimmedBody)
                        obj.optJSONArray("data") ?: JSONArray()
                    } else {
                        JSONArray(trimmedBody)
                    }

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val cat = obj.optString("category", "").trim()
                        val title = obj.optString("title", "").trim()
                        val content = obj.optString("content", "").trim()
                        val isAi = obj.optBoolean("isAi", false) || 
                                   obj.optString("type", "").lowercase().contains("ai") ||
                                   obj.optBoolean("isAiPrompt", false)
                        if (cat.isNotEmpty() && content.isNotEmpty()) {
                            list.add(SheetRow(category = cat, title = title, content = content, isAi = isAi))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SheetSync", "Parse response JSON failure", e)
                }
                return list
            } else {
                val records = parseFullCsv(body)
                if (records.isEmpty()) return emptyList()

                var categoryIndex = 0
                var titleIndex = -1
                var contentIndex = -1
                var isAiIndex = -1

                val firstRecord = records[0]
                var hasHeader = false
                if (firstRecord.any { it.equals("category", ignoreCase = true) || it.equals("title", ignoreCase = true) || it.equals("content", ignoreCase = true) }) {
                    hasHeader = true
                    categoryIndex = firstRecord.indexOfFirst { it.equals("category", ignoreCase = true) }.coerceAtLeast(0)
                    titleIndex = firstRecord.indexOfFirst { it.equals("title", ignoreCase = true) }
                    contentIndex = firstRecord.indexOfFirst { it.equals("content", ignoreCase = true) }
                    isAiIndex = firstRecord.indexOfFirst { it.equals("type", ignoreCase = true) || it.equals("isai", ignoreCase = true) || it.equals("is_ai", ignoreCase = true) || it.equals("isaiprompt", ignoreCase = true) }
                }

                if (titleIndex == -1) titleIndex = 1
                if (contentIndex == -1) contentIndex = 2
                if (isAiIndex == -1) isAiIndex = 3

                val startRow = if (hasHeader) 1 else 0
                val list = mutableListOf<SheetRow>()

                for (idx in startRow until records.size) {
                    val cells = records[idx]
                    if (cells.size <= 1) continue

                    val categoryName = cells.getOrNull(categoryIndex)?.trim() ?: ""
                    val title = cells.getOrNull(titleIndex)?.trim() ?: "Template $idx"
                    val content = cells.getOrNull(contentIndex)?.trim() ?: ""
                    val isAiVal = if (isAiIndex >= 0) cells.getOrNull(isAiIndex)?.trim() else null
                    val isAi = isAiVal?.equals("true", ignoreCase = true) == true || 
                               isAiVal?.equals("ai", ignoreCase = true) == true || 
                               isAiVal?.equals("1") == true

                    if (categoryName.isNotBlank() && content.isNotBlank()) {
                        list.add(SheetRow(category = categoryName, title = title, content = content, isAi = isAi))
                    }
                }
                return list
            }
        }
    }

    private suspend fun writeRemoteRows(url: String, rows: List<SheetRow>): Boolean {
        if (!url.contains("script.google.com")) return false
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val jsonArray = JSONArray()
        for (row in rows) {
            val obj = JSONObject()
            obj.put("category", row.category)
            obj.put("title", row.title)
            obj.put("content", row.content)
            obj.put("isAi", row.isAi)
            obj.put("type", if (row.isAi) "ai" else "template")
            jsonArray.put(obj)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonArray.toString().toRequestBody(mediaType)

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SheetSync", "Failed to upload sheet rows", e)
            false
        }
    }

    suspend fun performBidirectionalSync(forceUrl: String? = null, onFinished: ((Boolean, String) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val syncUrl = forceUrl ?: _remoteUrlSyncUrl.value.trim()
        if (syncUrl.isBlank()) {
            updateRemoteUrlSyncStatus("Config Error: Sync URL is empty.")
            onFinished?.invoke(false, "Sync URL is empty")
            return@withContext
        }

        if (isSyncing.value) {
            onFinished?.invoke(false, "Sync already in progress")
            return@withContext
        }
        isSyncing.value = true
        updateRemoteUrlSyncStatus("Synchronizing...")

        try {
            // 1. Get all local data
            val localCategories = database.categoryDao().getAllCategories().first()
            val localReplies = database.replyDao().getAllReplies().first()
            
            val localRows = localReplies.mapNotNull { reply ->
                localCategories.find { it.id == reply.categoryId }?.let { category ->
                    SheetRow(
                        category = category.name,
                        title = reply.title,
                        content = reply.content,
                        isAi = reply.isAiPrompt
                    )
                }
            }

            // 2. Fetch remote data
            val remoteRows = fetchRemoteRows(syncUrl)

            // 3. Bidirectional merge (preventing washouts!)
            val append = _remoteUrlSyncAppend.value
            val mergedRows = if (append) {
                mergeRows(localRows, remoteRows)
            } else {
                // If append is false, we still don't washout! We just consolidate them both ways.
                mergeRows(localRows, remoteRows)
            }

            // 4. Update the local Room database to match
            database.replyDao().deleteAllReplies()
            database.categoryDao().deleteAllCategories()

            val createdCategories = mutableMapOf<String, Long>()
            var orderIdx = 0
            for (row in mergedRows) {
                var categoryId = createdCategories[row.category.lowercase().trim()]
                if (categoryId == null) {
                    categoryId = database.categoryDao().insertCategory(
                        Category(
                            name = row.category.trim(),
                            iconName = if (row.isAi) "psychology" else "folder",
                            orderIndex = orderIdx++,
                            isForAi = row.isAi
                        )
                    )
                    createdCategories[row.category.lowercase().trim()] = categoryId
                }
                database.replyDao().insertReply(
                    Reply(
                        categoryId = categoryId,
                        title = row.title.trim(),
                        content = row.content.trim(),
                        isAiPrompt = row.isAi
                    )
                )
            }

            // 5. If it is a script.google.com URL, push the merged dataset back to Sheets
            var uploaded = false
            if (syncUrl.contains("script.google.com")) {
                uploaded = writeRemoteRows(syncUrl, mergedRows)
            }

            val timestamp = System.currentTimeMillis()
            updateRemoteUrlLastSyncTime(timestamp)
            val statusText = if (uploaded) {
                "Synced bi-directionally (${mergedRows.size} total items)"
            } else {
                "Synced (Read-only, ${mergedRows.size} total items)"
            }
            updateRemoteUrlSyncStatus(statusText)
            _toastMessage.emit("Sync complete! Consolidated ${mergedRows.size} items. 🚀")
            onFinished?.invoke(true, statusText)
        } catch (e: Exception) {
            Log.e("SheetSync", "Failed to sync", e)
            val errMsg = "Failed: ${e.message}"
            updateRemoteUrlSyncStatus(errMsg)
            onFinished?.invoke(false, errMsg)
        } finally {
            isSyncing.value = false
        }
    }

    suspend fun restoreDatabaseFromJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("app") || root.getString("app") != "Replai") {
                return@withContext false
            }

            // Clear out replies and categories for an exact sync override
            database.replyDao().deleteAllReplies()
            database.categoryDao().deleteAllCategories()

            val dataArray = root.getJSONArray("data")
            for (i in 0 until dataArray.length()) {
                val catObj = dataArray.getJSONObject(i)
                val catName = catObj.getString("name")
                val catIcon = catObj.optString("iconName", "folder")
                val catOrder = catObj.optInt("orderIndex", 0)
                val catIsForAi = catObj.optBoolean("isForAi", false)

                val categoryId = database.categoryDao().insertCategory(
                    Category(name = catName, iconName = catIcon, orderIndex = catOrder, isForAi = catIsForAi)
                )

                val repliesArray = catObj.getJSONArray("replies")
                for (j in 0 until repliesArray.length()) {
                    val replyObj = repliesArray.getJSONObject(j)
                    val rTitle = replyObj.getString("title")
                    val rContent = replyObj.getString("content")
                    val rCount = replyObj.optInt("usageCount", 0)
                    val rIsAi = replyObj.optBoolean("isAiPrompt", false)

                    database.replyDao().insertReply(
                        Reply(
                            categoryId = categoryId,
                            title = rTitle,
                            content = rContent,
                            usageCount = rCount,
                            isAiPrompt = rIsAi
                        )
                    )
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("ReplyViewModel", "Restore failed", e)
            return@withContext false
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
            repository.seedDatabaseIfNeeded()
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

                val repliesArray = JSONArray()
                val matchedReplies = repliesList.filter { it.categoryId == category.id }
                matchedReplies.forEach { reply ->
                    val replyObj = JSONObject()
                    replyObj.put("title", reply.title)
                    replyObj.put("content", reply.content)
                    replyObj.put("usageCount", reply.usageCount)
                    replyObj.put("isAiPrompt", reply.isAiPrompt)
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

                // Check if category already matches name
                var category = database.categoryDao().getCategoryByName(catName)
                val categoryId = if (category != null) {
                    category.id
                } else {
                    database.categoryDao().insertCategory(
                        Category(name = catName, iconName = catIcon, orderIndex = catOrder, isForAi = catIsForAi)
                    )
                }

                val repliesArray = catObj.getJSONArray("replies")
                for (j in 0 until repliesArray.length()) {
                    val replyObj = repliesArray.getJSONObject(j)
                    val rTitle = replyObj.getString("title")
                    val rContent = replyObj.getString("content")
                    val rCount = replyObj.optInt("usageCount", 0)
                    val rIsAi = replyObj.optBoolean("isAiPrompt", false)

                    // Simply insert all replies (can have duplicates or duplicates checked as needed)
                    database.replyDao().insertReply(
                        Reply(
                            categoryId = categoryId,
                            title = rTitle,
                            content = rContent,
                            usageCount = rCount,
                            isAiPrompt = rIsAi
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

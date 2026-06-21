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

    // Filtered replies flow based on search query and reply sort mode
    val filteredReplies: StateFlow<List<Reply>> = combine(allReplies, _searchQuery, _replySortOrder) { replies, query, sort ->
        val filtered = if (query.isBlank()) {
            replies
        } else {
            replies.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            "alphabetical" -> filtered.sortedBy { it.title.lowercase() }
            "usage" -> filtered.sortedByDescending { it.usageCount }
            else -> filtered.sortedByDescending { it.id } // "recent" sorts by ID descending
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    fun addCategory(name: String, iconName: String, isForAi: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val maxOrder = categories.value.maxOfOrNull { it.orderIndex } ?: 0
            repository.insertCategory(Category(name = name, iconName = iconName, orderIndex = maxOrder + 1, isForAi = isForAi))
            _toastMessage.emit("Category '$name' created")
        }
    }

    fun updateCategory(category: Category, newName: String, newIcon: String, isForAi: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(category.copy(name = newName, iconName = newIcon, isForAi = isForAi))
            _toastMessage.emit("Category updated")
        }
    }

    fun updateCategoriesOrder(reordered: List<Category>) {
        viewModelScope.launch(Dispatchers.IO) {
            reordered.forEach { category ->
                repository.updateCategory(category)
            }
            _toastMessage.emit("Category order updated")
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(category)
            _toastMessage.emit("Category '${category.name}' deleted")
        }
    }

    // Reply actions
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
        }
    }

    fun deleteReply(reply: Reply) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReply(reply)
            _toastMessage.emit("Reply '${reply.title}' deleted")
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
            } catch (e: Exception) {
                _toastMessage.emit("Failed to delete templates")
            }
        }
    }

    fun incrementUsage(replyId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.incrementUsageCount(replyId)
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

                // Check if category already matches name
                var category = database.categoryDao().getCategoryByName(catName)
                val categoryId = if (category != null) {
                    category.id
                } else {
                    database.categoryDao().insertCategory(
                        Category(name = catName, iconName = catIcon, orderIndex = catOrder)
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
}

package com.frqtools.replai.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ReplyRepository(
    private val categoryDao: CategoryDao,
    private val replyDao: ReplyDao
) {
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allReplies: Flow<List<Reply>> = replyDao.getAllReplies()

    fun getRepliesForCategory(categoryId: Long): Flow<List<Reply>> {
        return replyDao.getRepliesForCategory(categoryId)
    }

    fun searchReplies(query: String): Flow<List<Reply>> {
        val formattedQuery = "%$query%"
        return replyDao.searchReplies(formattedQuery)
    }

    suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category)
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
    }

    suspend fun insertReply(reply: Reply): Long {
        return replyDao.insertReply(reply)
    }

    suspend fun updateReply(reply: Reply) {
        replyDao.updateReply(reply)
    }

    suspend fun deleteReply(reply: Reply) {
        replyDao.deleteReply(reply)
    }

    suspend fun incrementUsageCount(replyId: Long) {
        replyDao.incrementUsageCount(replyId)
    }

    /**
     * Seeds default categories and replies if the database is completely empty.
     */
    suspend fun seedDatabaseIfNeeded() {
        val count = categoryDao.getCategoryCount()
        if (count > 0) {
            Log.d("ReplyRepository", "Database already seeded. Count: $count")
            return
        }

        Log.d("ReplyRepository", "Seeding default categories and replies...")

        val defaults = listOf(
            DefaultCategorySpec(
                name = "Quick Replies",
                icon = "chat",
                replies = listOf(
                    DefaultReplySpec("Support Greeting", "Hi there! Welcome to Replai support. How can we assist you today?"),
                    DefaultReplySpec("Personalized Wave", "Hello {name}! Thank you for reaching out to us. How can we help you today?")
                )
            ),
            DefaultCategorySpec(
                name = "AI Assistants",
                icon = "psychology",
                replies = listOf(
                    DefaultReplySpec("Improve Writing", "Act as a professional writer. Rephrase the following text to make it more professional, polite, and engaging:\n\"{text}\"", isAiPrompt = true)
                ),
                isForAi = true
            )
        )

        var idx = 0
        for (spec in defaults) {
            val catId = categoryDao.insertCategory(
                Category(name = spec.name, iconName = spec.icon, orderIndex = idx++, isForAi = spec.isForAi)
            )
            for (replySpec in spec.replies) {
                replyDao.insertReply(
                    Reply(
                        categoryId = catId,
                        title = replySpec.title,
                        content = replySpec.content,
                        isAiPrompt = replySpec.isAiPrompt
                    )
                )
            }
        }
    }

    private data class DefaultCategorySpec(
        val name: String,
        val icon: String,
        val replies: List<DefaultReplySpec>,
        val isForAi: Boolean = false
    )

    private data class DefaultReplySpec(
        val title: String,
        val content: String,
        val isAiPrompt: Boolean = false
    )
}

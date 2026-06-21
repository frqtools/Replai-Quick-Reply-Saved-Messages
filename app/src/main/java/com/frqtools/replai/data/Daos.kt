package com.frqtools.replai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY orderIndex ASC, name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
}

@Dao
interface ReplyDao {
    @Query("SELECT * FROM replies ORDER BY usageCount DESC, createdAt DESC")
    fun getAllReplies(): Flow<List<Reply>>

    @Query("SELECT * FROM replies WHERE categoryId = :categoryId ORDER BY usageCount DESC, createdAt DESC")
    fun getRepliesForCategory(categoryId: Long): Flow<List<Reply>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReply(reply: Reply): Long

    @Update
    suspend fun updateReply(reply: Reply)

    @Delete
    suspend fun deleteReply(reply: Reply)

    @Query("UPDATE replies SET usageCount = usageCount + 1 WHERE id = :replyId")
    suspend fun incrementUsageCount(replyId: Long)

    @Query("SELECT * FROM replies WHERE title LIKE :query OR content LIKE :query ORDER BY usageCount DESC")
    fun searchReplies(query: String): Flow<List<Reply>>

    @Query("SELECT COUNT(*) FROM replies")
    suspend fun getReplyCount(): Int
}

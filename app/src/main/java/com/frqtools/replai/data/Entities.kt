package com.frqtools.replai.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String = "folder",
    val orderIndex: Int = 0,
    val isForAi: Boolean = false,
    val colorHex: String = "#6200EE"
)

@Entity(
    tableName = "replies",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class Reply(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val title: String,
    val content: String,
    val usageCount: Int = 0,
    val isAiPrompt: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val lastUsedAt: Long = 0L
)

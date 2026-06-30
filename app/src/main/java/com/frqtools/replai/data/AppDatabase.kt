package com.frqtools.replai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.withTransaction

@Database(entities = [Category::class, Reply::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun replyDao(): ReplyDao

    suspend fun restoreFromBackup(categories: List<Category>, replies: List<Reply>) {
        withTransaction {
            replyDao().deleteAllReplies()
            categoryDao().deleteAllCategories()
            categories.forEach { categoryDao().insertCategory(it) }
            replies.forEach { replyDao().insertReply(it) }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "replai_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

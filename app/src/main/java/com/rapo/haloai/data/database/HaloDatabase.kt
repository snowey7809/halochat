package com.rapo.haloai.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rapo.haloai.data.database.dao.ChatDao
import com.rapo.haloai.data.database.dao.ModelDao
import com.rapo.haloai.data.database.entities.ChatEntity
import com.rapo.haloai.data.database.entities.ChatSessionEntity
import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.converters.ModelStatusConverter
import com.rapo.haloai.data.database.converters.ModelFormatConverter

@Database(
    entities = [ChatEntity::class, ChatSessionEntity::class, ModelEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(ModelStatusConverter::class, ModelFormatConverter::class)
abstract class HaloDatabase : RoomDatabase() {
    
    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao
    
    companion object {
        @Volatile
        private var INSTANCE: HaloDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add performance metrics columns to chat_messages table
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN responseTimeMs INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN tokenCount INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN tokensPerSecond REAL"
                )
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add stopReason column to chat_messages table
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN stopReason TEXT"
                )
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create chat_sessions table
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_sessions (
                        sessionId TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastMessageAt INTEGER NOT NULL
                    )"""
                )
            }
        }
        
        fun getDatabase(context: Context): HaloDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HaloDatabase::class.java,
                    "halo_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration() // For development
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.emailclient.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.emailclient.data.local.dao.AccountDao
import com.emailclient.data.local.dao.EmailDao
import com.emailclient.data.local.dao.FolderDao
import com.emailclient.data.local.entities.AccountEntity
import com.emailclient.data.local.entities.EmailEntity
import com.emailclient.data.local.entities.FolderEntity

/**
 * Main Room database for the application
 */
@Database(
    entities = [
        AccountEntity::class,
        EmailEntity::class,
        FolderEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun emailDao(): EmailDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val DATABASE_NAME = "email_client_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add profileImageUri column to accounts table
                database.execSQL("ALTER TABLE accounts ADD COLUMN profileImageUri TEXT")
            }
        }
    }
}

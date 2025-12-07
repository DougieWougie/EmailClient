package com.emailclient.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun emailDao(): EmailDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val DATABASE_NAME = "email_client_db"
    }
}

package com.emailclient.di

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.emailclient.data.local.AppDatabase
import com.emailclient.data.local.dao.AccountDao
import com.emailclient.data.local.dao.EmailDao
import com.emailclient.data.local.dao.FolderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import javax.inject.Singleton

/**
 * Hilt module for providing database-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val PREFS_NAME = "database_encryption_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        // Handle migration from unencrypted to encrypted database
        handleUnencryptedDatabase(context)

        // Get or generate database encryption passphrase
        val passphrase = getDatabasePassphrase(context)
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)  // Enable SQLCipher encryption
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration() // For development only
            .build()
    }

    /**
     * Handles migration from unencrypted database to encrypted database.
     * For development, we simply delete the unencrypted database if it exists.
     * In production, you would migrate the data instead.
     */
    private fun handleUnencryptedDatabase(context: Context) {
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)

        // If database file doesn't exist, nothing to do
        if (!dbFile.exists()) {
            return
        }

        // Try to detect if it's an unencrypted database by checking if it can be opened
        // without encryption. If SQLCipher fails to open with passphrase, it's likely unencrypted.
        // We'll use a simple heuristic: check if the file starts with "SQLite format 3"
        try {
            val header = ByteArray(16)
            dbFile.inputStream().use { it.read(header) }
            val headerString = String(header, Charsets.UTF_8)

            // Unencrypted SQLite databases start with "SQLite format 3"
            if (headerString.startsWith("SQLite format 3")) {
                // Database is unencrypted - delete it for development
                // In production, you would migrate the data here
                dbFile.delete()

                // Also delete associated files
                context.getDatabasePath("${AppDatabase.DATABASE_NAME}-shm").delete()
                context.getDatabasePath("${AppDatabase.DATABASE_NAME}-wal").delete()
            }
        } catch (e: Exception) {
            // If we can't read the file, let Room handle it
        }
    }

    /**
     * Gets or generates a secure database encryption passphrase.
     * The passphrase is stored in EncryptedSharedPreferences, which uses
     * the Android Keystore for encryption key management.
     */
    private fun getDatabasePassphrase(context: Context): ByteArray {
        // Create or retrieve the master key for encryption
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Create encrypted shared preferences
        val encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Check if passphrase already exists
        val existingPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
        if (existingPassphrase != null) {
            // Decode from hex string
            return hexStringToByteArray(existingPassphrase)
        }

        // Generate a new random 256-bit passphrase
        val newPassphrase = ByteArray(32) // 256 bits
        SecureRandom().nextBytes(newPassphrase)

        // Store it as hex string
        encryptedPrefs.edit()
            .putString(KEY_DB_PASSPHRASE, byteArrayToHexString(newPassphrase))
            .apply()

        return newPassphrase
    }

    /**
     * Converts a byte array to a hex string for storage.
     */
    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Converts a hex string back to a byte array.
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    @Provides
    @Singleton
    fun provideAccountDao(database: AppDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    @Singleton
    fun provideEmailDao(database: AppDatabase): EmailDao {
        return database.emailDao()
    }

    @Provides
    @Singleton
    fun provideFolderDao(database: AppDatabase): FolderDao {
        return database.folderDao()
    }
}

package com.emailclient.data.repository

import com.emailclient.data.local.CredentialManager
import com.emailclient.data.local.dao.AccountDao
import com.emailclient.data.local.dao.FolderDao
import com.emailclient.data.local.entities.FolderEntity
import com.emailclient.data.local.entities.toDomain
import com.emailclient.data.local.entities.toEntity
import com.emailclient.data.remote.imap.IMAPService
import com.emailclient.data.remote.oauth.TokenManager
import com.emailclient.data.remote.smtp.SMTPService
import com.emailclient.domain.model.Account
import com.emailclient.domain.model.AuthenticationType
import com.emailclient.domain.model.FolderType
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation of AccountRepository with credential management and connection testing
 */
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val folderDao: FolderDao,
    private val credentialManager: CredentialManager,
    private val imapService: IMAPService,
    private val smtpService: SMTPService,
    private val tokenManager: TokenManager
) : AccountRepository {

    override fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccounts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAccountById(accountId: Long): Result<Account> {
        return try {
            val account = accountDao.getAccountById(accountId)
            if (account != null) {
                Result.Success(account.toDomain())
            } else {
                Result.Error(Exception("Account not found"))
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to get account")
        }
    }

    override suspend fun getAccountByEmail(email: String): Result<Account> {
        return try {
            val account = accountDao.getAccountByEmail(email)
            if (account != null) {
                Result.Success(account.toDomain())
            } else {
                Result.Error(Exception("Account not found"))
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to get account")
        }
    }

    override suspend fun getDefaultAccount(): Result<Account> {
        return try {
            val account = accountDao.getDefaultAccount()
            if (account != null) {
                Result.Success(account.toDomain())
            } else {
                Result.Error(Exception("No default account found"))
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to get default account")
        }
    }

    override suspend fun addAccount(account: Account, password: String): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Test connection before adding
            val connectionTest = testConnection(account, password)
            if (connectionTest !is Result.Success || !connectionTest.data) {
                return@withContext Result.Error(Exception("Connection test failed"))
            }

            // Add account to database
            val accountId = accountDao.insertAccount(account.toEntity())

            // Store password securely
            credentialManager.savePassword(accountId, password)

            // Fetch and store folders
            try {
                val store = imapService.connect(account, password)
                try {
                    val folders = imapService.fetchFolders(store, accountId)
                    val folderEntities = folders.map { folder ->
                        FolderEntity(
                            accountId = accountId,
                            name = folder.name,
                            displayName = folder.displayName,
                            type = folder.type,
                            unreadCount = 0,
                            totalCount = 0,
                            syncEnabled = folder.syncEnabled
                        )
                    }
                    folderDao.insertFolders(folderEntities)
                } finally {
                    imapService.disconnect(store)
                }
            } catch (e: Exception) {
                // If folder fetch fails, create default folders
                android.util.Log.w("AccountRepository", "Failed to fetch folders from server, creating defaults", e)
                createDefaultFolders(accountId)
            }

            // If this is the first account, make it default
            val accountCount = accountDao.getAccountCount()
            if (accountCount == 1) {
                accountDao.setDefaultAccount(accountId)
            }

            Result.Success(accountId)
        } catch (e: Exception) {
            Result.Error(e, "Failed to add account: ${e.message}")
        }
    }

    override suspend fun updateAccount(account: Account): Result<Unit> {
        return try {
            accountDao.updateAccount(account.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update account")
        }
    }

    override suspend fun setDefaultAccount(accountId: Long): Result<Unit> {
        return try {
            accountDao.clearDefaultAccounts()
            accountDao.setDefaultAccount(accountId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to set default account")
        }
    }

    override suspend fun setSyncEnabled(accountId: Long, enabled: Boolean): Result<Unit> {
        return try {
            accountDao.setSyncEnabled(accountId, enabled)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update sync settings")
        }
    }

    override suspend fun setAutoDownloadImages(accountId: Long, enabled: Boolean): Result<Unit> {
        return try {
            accountDao.setAutoDownloadImages(accountId, enabled)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update image download settings")
        }
    }

    override suspend fun deleteAccount(accountId: Long): Result<Unit> {
        return try {
            // Delete stored credentials
            credentialManager.deleteAllCredentials(accountId)

            // Delete account (cascades to folders and emails via foreign keys)
            accountDao.deleteAccountById(accountId)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete account: ${e.message}")
        }
    }

    override suspend fun testConnection(account: Account, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d("AccountRepo", "Testing IMAP connection to ${account.imapConfig.host}:${account.imapConfig.port}")

            // Test IMAP connection
            val store = imapService.connect(account, password)
            val imapConnected = store.isConnected
            imapService.disconnect(store)

            if (!imapConnected) {
                android.util.Log.e("AccountRepo", "IMAP connection failed - store not connected")
                return@withContext Result.Error(
                    Exception("IMAP connection failed - not connected"),
                    "IMAP connection failed - not connected"
                )
            }

            android.util.Log.d("AccountRepo", "IMAP connection successful, testing SMTP to ${account.smtpConfig.host}:${account.smtpConfig.port}")

            // Test SMTP connection
            try {
                smtpService.testConnection(account, password)
            } catch (e: Exception) {
                // SMTP test failed with detailed error from SMTPService
                android.util.Log.e("AccountRepo", "SMTP connection failed: ${e.message}", e)
                return@withContext Result.Error(e, e.message ?: "SMTP connection failed")
            }

            android.util.Log.d("AccountRepo", "Both IMAP and SMTP connections successful")
            Result.Success(true)
        } catch (e: Exception) {
            // IMAP test failed with detailed error from IMAPService
            android.util.Log.e("AccountRepo", "IMAP connection failed: ${e.message}", e)
            Result.Error(e, e.message ?: "Connection test failed")
        }
    }

    override suspend fun getPassword(accountId: Long): String? {
        return credentialManager.getPassword(accountId)
    }

    override suspend fun updatePassword(accountId: Long, password: String) {
        credentialManager.savePassword(accountId, password)
    }

    override suspend fun ensureFoldersExist(accountId: Long): Result<Unit> {
        return try {
            val folders = folderDao.getFoldersByAccount(accountId).first()
            if (folders.isEmpty()) {
                android.util.Log.d("AccountRepository", "Creating default folders for account $accountId")
                createDefaultFolders(accountId)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to ensure folders exist")
        }
    }

    /**
     * Create default folders when IMAP fetch fails
     */
    private suspend fun createDefaultFolders(accountId: Long) {
        val defaultFolders = listOf(
            FolderEntity(
                accountId = accountId,
                name = "INBOX",
                displayName = "Inbox",
                type = com.emailclient.domain.model.FolderType.INBOX,
                unreadCount = 0,
                totalCount = 0,
                syncEnabled = true
            ),
            FolderEntity(
                accountId = accountId,
                name = "Sent",
                displayName = "Sent",
                type = com.emailclient.domain.model.FolderType.SENT,
                unreadCount = 0,
                totalCount = 0,
                syncEnabled = true
            ),
            FolderEntity(
                accountId = accountId,
                name = "Drafts",
                displayName = "Drafts",
                type = com.emailclient.domain.model.FolderType.DRAFTS,
                unreadCount = 0,
                totalCount = 0,
                syncEnabled = false
            ),
            FolderEntity(
                accountId = accountId,
                name = "Trash",
                displayName = "Trash",
                type = com.emailclient.domain.model.FolderType.TRASH,
                unreadCount = 0,
                totalCount = 0,
                syncEnabled = false
            ),
            FolderEntity(
                accountId = accountId,
                name = "Spam",
                displayName = "Spam",
                type = com.emailclient.domain.model.FolderType.SPAM,
                unreadCount = 0,
                totalCount = 0,
                syncEnabled = false
            )
        )
        folderDao.insertFolders(defaultFolders)
    }

    // OAuth2-specific methods

    override suspend fun addOAuth2Account(
        account: Account,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("AccountRepository", "Adding OAuth2 account: ${account.email}")

            // Test OAuth2 connection first
            val testResult = testOAuth2Connection(account, accessToken)
            if (testResult !is Result.Success || !testResult.data) {
                return@withContext Result.Error(
                    Exception("OAuth2 connection test failed"),
                    "Failed to connect with OAuth2 credentials"
                )
            }

            // Insert account into database
            val accountId = accountDao.insertAccount(account.toEntity())
            android.util.Log.d("AccountRepository", "Account inserted with ID: $accountId")

            // Store OAuth2 tokens
            tokenManager.saveTokens(accountId, accessToken, refreshToken, expiresAt)
            android.util.Log.d("AccountRepository", "OAuth2 tokens saved for account $accountId")

            // Fetch folders from IMAP server
            try {
                val store = imapService.connect(account.copy(id = accountId), accessToken = accessToken)
                val folders = imapService.fetchFolders(store, accountId)
                imapService.disconnect(store)

                if (folders.isNotEmpty()) {
                    val folderEntities = folders.map { folder ->
                        FolderEntity(
                            accountId = accountId,
                            name = folder.name,
                            displayName = folder.displayName,
                            type = folder.type,
                            unreadCount = folder.unreadCount,
                            totalCount = folder.totalCount,
                            syncEnabled = folder.type == FolderType.INBOX
                        )
                    }
                    folderDao.insertFolders(folderEntities)
                    android.util.Log.d("AccountRepository", "Fetched ${folders.size} folders from server")
                }
            } catch (e: Exception) {
                android.util.Log.w("AccountRepository", "Failed to fetch folders from server, creating defaults", e)
                createDefaultFolders(accountId)
            }

            Result.Success(accountId)
        } catch (e: Exception) {
            android.util.Log.e("AccountRepository", "Failed to add OAuth2 account", e)
            Result.Error(e, "Failed to add OAuth2 account: ${e.message}")
        }
    }

    override suspend fun testOAuth2Connection(account: Account, accessToken: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("AccountRepository", "Testing OAuth2 connection for ${account.email}")

                // Test IMAP connection
                val imapStore = imapService.connect(account, accessToken = accessToken)
                imapService.disconnect(imapStore)
                android.util.Log.d("AccountRepository", "✓ IMAP OAuth2 connection successful")

                // Test SMTP connection
                smtpService.testConnection(account, accessToken = accessToken)
                android.util.Log.d("AccountRepository", "✓ SMTP OAuth2 connection successful")

                Result.Success(true)
            } catch (e: Exception) {
                android.util.Log.e("AccountRepository", "✗ OAuth2 connection test failed", e)
                Result.Error(e, "OAuth2 connection test failed: ${e.message}")
            }
        }

    override suspend fun refreshAccountToken(accountId: Long): Result<String> {
        return try {
            android.util.Log.d("AccountRepository", "Refreshing token for account $accountId")
            tokenManager.refreshTokenIfNeeded(accountId)
        } catch (e: Exception) {
            android.util.Log.e("AccountRepository", "Token refresh failed for account $accountId", e)
            Result.Error(e, "Failed to refresh token: ${e.message}")
        }
    }
}

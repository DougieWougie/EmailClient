package com.emailclient.data.repository

import com.emailclient.data.local.CredentialManager
import com.emailclient.data.local.dao.AccountDao
import com.emailclient.data.local.dao.FolderDao
import com.emailclient.data.local.entities.FolderEntity
import com.emailclient.data.local.entities.toDomain
import com.emailclient.data.local.entities.toEntity
import com.emailclient.data.remote.imap.IMAPService
import com.emailclient.data.remote.smtp.SMTPService
import com.emailclient.domain.model.Account
import com.emailclient.domain.model.FolderType
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of AccountRepository with credential management and connection testing
 */
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val folderDao: FolderDao,
    private val credentialManager: CredentialManager,
    private val imapService: IMAPService,
    private val smtpService: SMTPService
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

    override suspend fun addAccount(account: Account, password: String): Result<Long> {
        return try {
            // Test connection before adding
            val connectionTest = testConnection(account, password)
            if (connectionTest !is Result.Success || !connectionTest.data) {
                return Result.Error(Exception("Connection test failed"))
            }

            // Add account to database
            val accountId = accountDao.insertAccount(account.toEntity())

            // Store password securely
            credentialManager.savePassword(accountId, password)

            // Fetch and store folders
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

    override suspend fun testConnection(account: Account, password: String): Result<Boolean> {
        return try {
            // Test IMAP connection
            val store = imapService.connect(account, password)
            val imapConnected = store.isConnected
            imapService.disconnect(store)

            if (!imapConnected) {
                return Result.Error(Exception("IMAP connection failed"))
            }

            // Test SMTP connection
            val smtpConnected = smtpService.testConnection(account, password)

            if (!smtpConnected) {
                return Result.Error(Exception("SMTP connection failed"))
            }

            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e, "Connection test failed: ${e.message}")
        }
    }
}

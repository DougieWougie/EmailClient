package com.emailclient.data.repository

import com.emailclient.data.local.dao.AccountDao
import com.emailclient.data.local.entities.toDomain
import com.emailclient.data.local.entities.toEntity
import com.emailclient.domain.model.Account
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of AccountRepository
 *
 * Note: Credential storage will use EncryptedSharedPreferences.
 * Connection testing will use JavaMail library.
 */
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao
    // TODO: Inject EncryptedSharedPreferences or secure credential manager
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
            // TODO: Store password securely using EncryptedSharedPreferences
            // TODO: Test connection before adding
            val accountId = accountDao.insertAccount(account.toEntity())

            // If this is the first account, make it default
            val accountCount = accountDao.getAccountCount()
            if (accountCount == 1) {
                accountDao.setDefaultAccount(accountId)
            }

            Result.Success(accountId)
        } catch (e: Exception) {
            Result.Error(e, "Failed to add account")
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
            // TODO: Delete stored credentials
            accountDao.deleteAccountById(accountId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete account")
        }
    }

    override suspend fun testConnection(account: Account, password: String): Result<Boolean> {
        // TODO: Implement connection test using JavaMail
        // This should try to connect to both IMAP and SMTP servers
        return try {
            // Placeholder implementation
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e, "Connection test failed")
        }
    }
}

package com.emailclient.domain.repository

import com.emailclient.domain.model.Account
import com.emailclient.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for account operations
 */
interface AccountRepository {

    fun getAllAccounts(): Flow<List<Account>>

    suspend fun getAccountById(accountId: Long): Result<Account>

    suspend fun getAccountByEmail(email: String): Result<Account>

    suspend fun getDefaultAccount(): Result<Account>

    suspend fun addAccount(account: Account, password: String): Result<Long>

    suspend fun updateAccount(account: Account): Result<Unit>

    suspend fun setDefaultAccount(accountId: Long): Result<Unit>

    suspend fun setSyncEnabled(accountId: Long, enabled: Boolean): Result<Unit>

    suspend fun setAutoDownloadImages(accountId: Long, enabled: Boolean): Result<Unit>

    suspend fun deleteAccount(accountId: Long): Result<Unit>

    suspend fun testConnection(account: Account, password: String): Result<Boolean>

    suspend fun getPassword(accountId: Long): String?

    suspend fun updatePassword(accountId: Long, password: String)
}

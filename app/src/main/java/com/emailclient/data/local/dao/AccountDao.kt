package com.emailclient.data.local.dao

import androidx.room.*
import com.emailclient.data.local.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for account operations
 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, email ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :accountId")
    suspend fun getAccountById(accountId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE email = :email")
    suspend fun getAccountByEmail(email: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultAccount(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET isDefault = 0")
    suspend fun clearDefaultAccounts()

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :accountId")
    suspend fun setDefaultAccount(accountId: Long)

    @Query("UPDATE accounts SET syncEnabled = :enabled WHERE id = :accountId")
    suspend fun setSyncEnabled(accountId: Long, enabled: Boolean)

    @Query("UPDATE accounts SET autoDownloadImages = :enabled WHERE id = :accountId")
    suspend fun setAutoDownloadImages(accountId: Long, enabled: Boolean)

    @Query("UPDATE accounts SET lastSyncTime = :timestamp WHERE id = :accountId")
    suspend fun updateLastSyncTime(accountId: Long, timestamp: Long)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int
}

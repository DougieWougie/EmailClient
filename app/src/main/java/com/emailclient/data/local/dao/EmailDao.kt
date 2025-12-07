package com.emailclient.data.local.dao

import androidx.room.*
import com.emailclient.data.local.entities.EmailEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for email operations
 */
@Dao
interface EmailDao {

    @Query("SELECT * FROM emails WHERE folderId = :folderId ORDER BY receivedDate DESC")
    fun getEmailsByFolder(folderId: Long): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId ORDER BY receivedDate DESC")
    fun getEmailsByAccount(accountId: Long): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :emailId")
    suspend fun getEmailById(emailId: Long): EmailEntity?

    @Query("SELECT * FROM emails WHERE messageId = :messageId")
    suspend fun getEmailByMessageId(messageId: String): EmailEntity?

    @Query("""
        SELECT * FROM emails
        WHERE folderId = :folderId
        AND isRead = 0
        ORDER BY receivedDate DESC
    """)
    fun getUnreadEmails(folderId: Long): Flow<List<EmailEntity>>

    @Query("SELECT COUNT(*) FROM emails WHERE folderId = :folderId AND isRead = 0")
    fun getUnreadCount(folderId: Long): Flow<Int>

    @Query("""
        SELECT * FROM emails
        WHERE accountId = :accountId
        AND (subject LIKE '%' || :query || '%'
             OR body LIKE '%' || :query || '%'
             OR snippet LIKE '%' || :query || '%')
        ORDER BY receivedDate DESC
    """)
    fun searchEmails(accountId: Long, query: String): Flow<List<EmailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Update
    suspend fun updateEmail(email: EmailEntity)

    @Query("UPDATE emails SET isRead = :isRead WHERE id = :emailId")
    suspend fun markAsRead(emailId: Long, isRead: Boolean)

    @Query("UPDATE emails SET isFlagged = :isFlagged WHERE id = :emailId")
    suspend fun markAsFlagged(emailId: Long, isFlagged: Boolean)

    @Query("UPDATE emails SET folderId = :newFolderId WHERE id = :emailId")
    suspend fun moveToFolder(emailId: Long, newFolderId: Long)

    @Delete
    suspend fun deleteEmail(email: EmailEntity)

    @Query("DELETE FROM emails WHERE id = :emailId")
    suspend fun deleteEmailById(emailId: Long)

    @Query("DELETE FROM emails WHERE folderId = :folderId")
    suspend fun deleteEmailsByFolder(folderId: Long)

    @Query("DELETE FROM emails WHERE accountId = :accountId")
    suspend fun deleteEmailsByAccount(accountId: Long)
}

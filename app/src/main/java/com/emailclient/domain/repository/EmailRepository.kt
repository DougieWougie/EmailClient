package com.emailclient.domain.repository

import com.emailclient.domain.model.Email
import com.emailclient.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for email operations
 */
interface EmailRepository {

    fun getEmailsByFolder(folderId: Long): Flow<List<Email>>

    fun getEmailsByAccount(accountId: Long): Flow<List<Email>>

    suspend fun getEmailById(emailId: String): Result<Email>

    fun getUnreadEmails(folderId: Long): Flow<List<Email>>

    fun getUnreadCount(folderId: Long): Flow<Int>

    fun searchEmails(accountId: Long, query: String): Flow<List<Email>>

    suspend fun syncEmails(accountId: Long, folderId: Long): Result<Unit>

    suspend fun sendEmail(
        accountId: Long,
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): Result<Unit>

    suspend fun markAsRead(emailId: String, isRead: Boolean): Result<Unit>

    suspend fun markAsFlagged(emailId: String, isFlagged: Boolean): Result<Unit>

    suspend fun moveToFolder(emailId: String, newFolderId: Long): Result<Unit>

    suspend fun deleteEmail(emailId: String): Result<Unit>

    suspend fun archiveEmail(emailId: String): Result<Unit>
}

package com.emailclient.data.repository

import com.emailclient.data.local.CredentialManager
import com.emailclient.data.local.dao.AccountDao
import com.emailclient.data.local.dao.EmailDao
import com.emailclient.data.local.dao.FolderDao
import com.emailclient.data.local.entities.toDomain
import com.emailclient.data.local.entities.toEntity
import com.emailclient.data.remote.imap.IMAPService
import com.emailclient.data.remote.smtp.SMTPService
import com.emailclient.domain.model.Email
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of EmailRepository with IMAP/SMTP support
 */
class EmailRepositoryImpl @Inject constructor(
    private val emailDao: EmailDao,
    private val accountDao: AccountDao,
    private val folderDao: FolderDao,
    private val credentialManager: CredentialManager,
    private val imapService: IMAPService,
    private val smtpService: SMTPService
) : EmailRepository {

    override fun getEmailsByFolder(folderId: Long): Flow<List<Email>> {
        return emailDao.getEmailsByFolder(folderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEmailsByAccount(accountId: Long): Flow<List<Email>> {
        return emailDao.getEmailsByAccount(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEmailById(emailId: String): Result<Email> {
        return try {
            val email = emailDao.getEmailById(emailId.toLong())
            if (email != null) {
                Result.Success(email.toDomain())
            } else {
                Result.Error(Exception("Email not found"))
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to get email")
        }
    }

    override fun getUnreadEmails(folderId: Long): Flow<List<Email>> {
        return emailDao.getUnreadEmails(folderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUnreadCount(folderId: Long): Flow<Int> {
        return emailDao.getUnreadCount(folderId)
    }

    override fun searchEmails(accountId: Long, query: String): Flow<List<Email>> {
        return emailDao.searchEmails(accountId, query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun syncEmails(accountId: Long, folderId: Long): Result<Unit> {
        return try {
            // Get account and credentials
            val accountEntity = accountDao.getAccountById(accountId)
                ?: return Result.Error(Exception("Account not found"))

            val password = credentialManager.getPassword(accountId)
                ?: return Result.Error(Exception("Password not found"))

            val account = accountEntity.toDomain()

            // Get folder info
            val folderEntity = folderDao.getFolderById(folderId)
                ?: return Result.Error(Exception("Folder not found"))

            // Connect to IMAP and fetch emails
            val store = imapService.connect(account, password)

            try {
                val emails = imapService.fetchEmails(
                    store = store,
                    accountId = accountId,
                    folderId = folderId,
                    folderName = folderEntity.name,
                    limit = 50
                )

                // Store emails in local database
                emailDao.insertEmails(emails.map { it.toEntity() })

                // Update folder counts
                val unreadCount = emails.count { !it.isRead }
                folderDao.updateUnreadCount(folderId, unreadCount)
                folderDao.updateTotalCount(folderId, emails.size)

                // Update last sync time
                accountDao.updateLastSyncTime(accountId, System.currentTimeMillis())

                Result.Success(Unit)
            } finally {
                imapService.disconnect(store)
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to sync emails: ${e.message}")
        }
    }

    override suspend fun sendEmail(
        accountId: Long,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        body: String,
        isHtml: Boolean
    ): Result<Unit> {
        return try {
            // Get account and credentials
            val accountEntity = accountDao.getAccountById(accountId)
                ?: return Result.Error(Exception("Account not found"))

            val password = credentialManager.getPassword(accountId)
                ?: return Result.Error(Exception("Password not found"))

            val account = accountEntity.toDomain()

            // Send email via SMTP
            val success = smtpService.sendEmail(
                account = account,
                password = password,
                to = to,
                cc = cc,
                bcc = bcc,
                subject = subject,
                body = body,
                isHtml = isHtml
            )

            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to send email"))
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to send email: ${e.message}")
        }
    }

    override suspend fun markAsRead(emailId: String, isRead: Boolean): Result<Unit> {
        return try {
            emailDao.markAsRead(emailId.toLong(), isRead)
            // TODO: Also mark as read on the server using IMAP
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to mark email as read")
        }
    }

    override suspend fun markAsFlagged(emailId: String, isFlagged: Boolean): Result<Unit> {
        return try {
            emailDao.markAsFlagged(emailId.toLong(), isFlagged)
            // TODO: Also mark as flagged on the server using IMAP
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to mark email as flagged")
        }
    }

    override suspend fun moveToFolder(emailId: String, newFolderId: Long): Result<Unit> {
        return try {
            emailDao.moveToFolder(emailId.toLong(), newFolderId)
            // TODO: Also move on the server using IMAP
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to move email")
        }
    }

    override suspend fun deleteEmail(emailId: String): Result<Unit> {
        return try {
            emailDao.deleteEmailById(emailId.toLong())
            // TODO: Also delete on the server using IMAP
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete email")
        }
    }
}

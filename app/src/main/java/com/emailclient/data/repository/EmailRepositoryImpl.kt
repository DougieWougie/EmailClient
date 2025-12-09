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

                // Upsert emails in local database - this will:
                // - Insert new emails
                // - Update flags (isRead, isFlagged) for existing emails from server state
                emailDao.upsertEmails(emails.map { it.toEntity() })

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
            smtpService.sendEmail(
                account = account,
                password = password,
                to = to,
                cc = cc,
                bcc = bcc,
                subject = subject,
                body = body,
                isHtml = isHtml
            )

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "Failed to send email")
        }
    }

    override suspend fun markAsRead(emailId: String, isRead: Boolean): Result<Unit> {
        return try {
            // Get email details
            val emailEntity = emailDao.getEmailById(emailId.toLong())
                ?: return Result.Error(Exception("Email not found"))

            val email = emailEntity.toDomain()

            // Update local database first
            emailDao.markAsRead(emailId.toLong(), isRead)

            // Update on server
            try {
                val accountEntity = accountDao.getAccountById(email.accountId)
                    ?: return Result.Error(Exception("Account not found"))

                val password = credentialManager.getPassword(email.accountId)
                    ?: return Result.Error(Exception("Password not found"))

                val folderEntity = folderDao.getFolderById(email.folderId)
                    ?: return Result.Error(Exception("Folder not found"))

                val account = accountEntity.toDomain()

                // Connect and update flag on server
                val store = imapService.connect(account, password)
                try {
                    val success = imapService.setReadFlag(
                        store = store,
                        folderName = folderEntity.name,
                        messageId = email.messageId,
                        isRead = isRead
                    )

                    if (!success) {
                        android.util.Log.w("EmailRepo", "Failed to update read flag on server, but local change succeeded")
                    }
                } finally {
                    imapService.disconnect(store)
                }
            } catch (e: Exception) {
                // Log but don't fail - local change already succeeded
                android.util.Log.e("EmailRepo", "Failed to sync read flag to server", e)
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to mark email as read")
        }
    }

    override suspend fun markAsFlagged(emailId: String, isFlagged: Boolean): Result<Unit> {
        return try {
            // Get email details
            val emailEntity = emailDao.getEmailById(emailId.toLong())
                ?: return Result.Error(Exception("Email not found"))

            val email = emailEntity.toDomain()

            // Update local database first
            emailDao.markAsFlagged(emailId.toLong(), isFlagged)

            // Update on server
            try {
                val accountEntity = accountDao.getAccountById(email.accountId)
                    ?: return Result.Error(Exception("Account not found"))

                val password = credentialManager.getPassword(email.accountId)
                    ?: return Result.Error(Exception("Password not found"))

                val folderEntity = folderDao.getFolderById(email.folderId)
                    ?: return Result.Error(Exception("Folder not found"))

                val account = accountEntity.toDomain()

                // Connect and update flag on server
                val store = imapService.connect(account, password)
                try {
                    val success = imapService.setFlaggedFlag(
                        store = store,
                        folderName = folderEntity.name,
                        messageId = email.messageId,
                        isFlagged = isFlagged
                    )

                    if (!success) {
                        android.util.Log.w("EmailRepo", "Failed to update flagged flag on server, but local change succeeded")
                    }
                } finally {
                    imapService.disconnect(store)
                }
            } catch (e: Exception) {
                // Log but don't fail - local change already succeeded
                android.util.Log.e("EmailRepo", "Failed to sync flagged flag to server", e)
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to mark email as flagged")
        }
    }

    override suspend fun moveToFolder(emailId: String, newFolderId: Long): Result<Unit> {
        return try {
            // Get email details
            val emailEntity = emailDao.getEmailById(emailId.toLong())
                ?: return Result.Error(Exception("Email not found"))

            val email = emailEntity.toDomain()

            // Get source and target folder names
            val sourceFolderEntity = folderDao.getFolderById(email.folderId)
                ?: return Result.Error(Exception("Source folder not found"))

            val targetFolderEntity = folderDao.getFolderById(newFolderId)
                ?: return Result.Error(Exception("Target folder not found"))

            // Update local database first
            emailDao.moveToFolder(emailId.toLong(), newFolderId)

            // Update on server
            try {
                val accountEntity = accountDao.getAccountById(email.accountId)
                    ?: return Result.Error(Exception("Account not found"))

                val password = credentialManager.getPassword(email.accountId)
                    ?: return Result.Error(Exception("Password not found"))

                val account = accountEntity.toDomain()

                // Connect and move message on server
                val store = imapService.connect(account, password)
                try {
                    val success = imapService.moveMessage(
                        store = store,
                        sourceFolderName = sourceFolderEntity.name,
                        targetFolderName = targetFolderEntity.name,
                        messageId = email.messageId
                    )

                    if (!success) {
                        android.util.Log.w("EmailRepo", "Failed to move message on server, but local change succeeded")
                    }
                } finally {
                    imapService.disconnect(store)
                }
            } catch (e: Exception) {
                // Log but don't fail - local change already succeeded
                android.util.Log.e("EmailRepo", "Failed to move message on server", e)
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to move email")
        }
    }

    override suspend fun deleteEmail(emailId: String): Result<Unit> {
        return try {
            // Get email details before deleting
            val emailEntity = emailDao.getEmailById(emailId.toLong())
                ?: return Result.Error(Exception("Email not found"))

            val email = emailEntity.toDomain()

            // Delete from local database first
            emailDao.deleteEmailById(emailId.toLong())

            // Delete on server
            try {
                val accountEntity = accountDao.getAccountById(email.accountId)
                    ?: return Result.Error(Exception("Account not found"))

                val password = credentialManager.getPassword(email.accountId)
                    ?: return Result.Error(Exception("Password not found"))

                val folderEntity = folderDao.getFolderById(email.folderId)
                    ?: return Result.Error(Exception("Folder not found"))

                val account = accountEntity.toDomain()

                // Connect and delete message on server
                val store = imapService.connect(account, password)
                try {
                    val success = imapService.deleteMessage(
                        store = store,
                        folderName = folderEntity.name,
                        messageId = email.messageId
                    )

                    if (!success) {
                        android.util.Log.w("EmailRepo", "Failed to delete message on server, but local deletion succeeded")
                    }
                } finally {
                    imapService.disconnect(store)
                }
            } catch (e: Exception) {
                // Log but don't fail - local deletion already succeeded
                android.util.Log.e("EmailRepo", "Failed to delete message on server", e)
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete email")
        }
    }

    override suspend fun archiveEmail(emailId: String): Result<Unit> {
        return try {
            // Get email details
            val emailEntity = emailDao.getEmailById(emailId.toLong())
                ?: return Result.Error(Exception("Email not found"))

            val email = emailEntity.toDomain()

            // Get archive folder for this account
            var archiveFolderId: Long? = null

            // First try to get folder by ARCHIVE type
            val archiveFolder = folderDao.getFolderByType(email.accountId, com.emailclient.domain.model.FolderType.ARCHIVE)

            if (archiveFolder != null) {
                archiveFolderId = archiveFolder.id
            } else {
                // If not found, look for a folder with "archive" in the name
                val accountEntity = accountDao.getAccountById(email.accountId)
                if (accountEntity != null) {
                    val password = credentialManager.getPassword(email.accountId)
                    if (password != null) {
                        val account = accountEntity.toDomain()
                        val store = imapService.connect(account, password)
                        try {
                            val folders = imapService.fetchFolders(store, email.accountId)
                            val archiveFolderFromServer = folders.firstOrNull {
                                it.name.contains("archive", ignoreCase = true) ||
                                it.type == com.emailclient.domain.model.FolderType.ARCHIVE
                            }

                            if (archiveFolderFromServer != null) {
                                // Insert folder if not exists
                                val existing = folderDao.getFolderByName(email.accountId, archiveFolderFromServer.name)
                                archiveFolderId = existing?.id ?: folderDao.insertFolder(
                                    com.emailclient.data.local.entities.FolderEntity(
                                        accountId = email.accountId,
                                        name = archiveFolderFromServer.name,
                                        displayName = archiveFolderFromServer.displayName,
                                        type = com.emailclient.domain.model.FolderType.ARCHIVE,
                                        unreadCount = 0,
                                        totalCount = 0,
                                        syncEnabled = true
                                    )
                                )
                            }
                        } finally {
                            imapService.disconnect(store)
                        }
                    }
                }
            }

            if (archiveFolderId == null) {
                return Result.Error(Exception("Archive folder not found. Please sync folders or create an Archive folder."))
            }

            // Move email to archive folder
            moveToFolder(emailId, archiveFolderId)
        } catch (e: Exception) {
            Result.Error(e, "Failed to archive email: ${e.message}")
        }
    }
}

package com.emailclient.data.repository

import com.emailclient.data.local.CredentialManager
import com.emailclient.data.local.dao.AccountDao
import com.emailclient.data.local.dao.FolderDao
import com.emailclient.data.local.entities.FolderEntity
import com.emailclient.data.local.entities.toDomain
import com.emailclient.data.remote.imap.IMAPService
import com.emailclient.domain.model.Folder
import com.emailclient.domain.model.FolderType
import com.emailclient.domain.repository.FolderRepository
import com.emailclient.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of FolderRepository with IMAP folder sync
 */
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val accountDao: AccountDao,
    private val credentialManager: CredentialManager,
    private val imapService: IMAPService
) : FolderRepository {

    override fun getFoldersByAccount(accountId: Long): Flow<List<Folder>> {
        return folderDao.getFoldersByAccount(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFolderById(folderId: Long): Result<Folder> {
        return try {
            val folder = folderDao.getFolderById(folderId)
            if (folder != null) {
                Result.Success(folder.toDomain())
            } else {
                Result.Error(Exception("Folder not found"))
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to get folder")
        }
    }

    override suspend fun getFolderByName(accountId: Long, name: String): Result<Folder> {
        return try {
            val folder = folderDao.getFolderByName(accountId, name)
            if (folder != null) {
                Result.Success(folder.toDomain())
            } else {
                Result.Error(Exception("Folder not found"))
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to get folder")
        }
    }

    override suspend fun getFolderByType(accountId: Long, type: FolderType): Result<Folder> {
        return try {
            val folder = folderDao.getFolderByType(accountId, type)
            if (folder != null) {
                Result.Success(folder.toDomain())
            } else {
                Result.Error(Exception("Folder not found"))
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to get folder")
        }
    }

    override suspend fun syncFolders(accountId: Long): Result<Unit> {
        return try {
            // Get account and credentials
            val accountEntity = accountDao.getAccountById(accountId)
                ?: return Result.Error(Exception("Account not found"))

            val password = credentialManager.getPassword(accountId)
                ?: return Result.Error(Exception("Password not found"))

            val account = accountEntity.toDomain()

            // Connect to IMAP and fetch folders
            val store = imapService.connect(account, password)

            try {
                val folders = imapService.fetchFolders(store, accountId)

                // Update existing folders or insert new ones
                folders.forEach { folder ->
                    val existing = folderDao.getFolderByName(accountId, folder.name)
                    if (existing != null) {
                        // Update existing folder
                        folderDao.updateFolder(
                            existing.copy(
                                displayName = folder.displayName,
                                type = folder.type
                            )
                        )
                    } else {
                        // Insert new folder
                        folderDao.insertFolder(
                            FolderEntity(
                                accountId = accountId,
                                name = folder.name,
                                displayName = folder.displayName,
                                type = folder.type,
                                unreadCount = 0,
                                totalCount = 0,
                                syncEnabled = folder.syncEnabled
                            )
                        )
                    }
                }

                Result.Success(Unit)
            } finally {
                imapService.disconnect(store)
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to sync folders: ${e.message}")
        }
    }

    override suspend fun createFolder(accountId: Long, folderName: String): Result<Long> {
        // TODO: Implement IMAP folder creation using JavaMail
        return try {
            // Placeholder implementation
            Result.Success(0L)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create folder")
        }
    }

    override suspend fun renameFolder(folderId: Long, newName: String): Result<Unit> {
        // TODO: Implement IMAP folder rename using JavaMail
        return try {
            // Placeholder implementation
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to rename folder")
        }
    }

    override suspend fun deleteFolder(folderId: Long): Result<Unit> {
        // TODO: Implement IMAP folder deletion using JavaMail
        return try {
            folderDao.deleteFolderById(folderId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete folder")
        }
    }
}

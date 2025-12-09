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
        return try {
            // Validate folder name
            if (folderName.isBlank()) {
                return Result.Error(Exception("Folder name cannot be empty"))
            }

            // Check if folder already exists locally
            val existing = folderDao.getFolderByName(accountId, folderName)
            if (existing != null) {
                return Result.Error(Exception("Folder already exists"))
            }

            // Get account and credentials
            val accountEntity = accountDao.getAccountById(accountId)
                ?: return Result.Error(Exception("Account not found"))

            val password = credentialManager.getPassword(accountId)
                ?: return Result.Error(Exception("Password not found"))

            val account = accountEntity.toDomain()

            // Connect to IMAP and create folder
            val store = imapService.connect(account, password)

            try {
                val success = imapService.createFolder(store, folderName)

                if (!success) {
                    return Result.Error(Exception("Failed to create folder on server"))
                }

                // Insert folder into local database
                val folderId = folderDao.insertFolder(
                    FolderEntity(
                        accountId = accountId,
                        name = folderName,
                        displayName = folderName,
                        type = FolderType.CUSTOM,
                        unreadCount = 0,
                        totalCount = 0,
                        syncEnabled = true
                    )
                )

                Result.Success(folderId)
            } finally {
                imapService.disconnect(store)
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to create folder: ${e.message}")
        }
    }

    override suspend fun renameFolder(folderId: Long, newName: String): Result<Unit> {
        return try {
            // Validate new name
            if (newName.isBlank()) {
                return Result.Error(Exception("Folder name cannot be empty"))
            }

            // Get folder info
            val folderEntity = folderDao.getFolderById(folderId)
                ?: return Result.Error(Exception("Folder not found"))

            // Can't rename system folders
            if (folderEntity.type != FolderType.CUSTOM) {
                return Result.Error(Exception("Cannot rename system folders"))
            }

            // Get account and credentials
            val accountEntity = accountDao.getAccountById(folderEntity.accountId)
                ?: return Result.Error(Exception("Account not found"))

            val password = credentialManager.getPassword(folderEntity.accountId)
                ?: return Result.Error(Exception("Password not found"))

            val account = accountEntity.toDomain()

            // Connect to IMAP and rename folder
            val store = imapService.connect(account, password)

            try {
                val success = imapService.renameFolder(
                    store = store,
                    oldName = folderEntity.name,
                    newName = newName
                )

                if (!success) {
                    return Result.Error(Exception("Failed to rename folder on server"))
                }

                // Update folder in local database
                folderDao.updateFolder(
                    folderEntity.copy(
                        name = newName,
                        displayName = newName
                    )
                )

                Result.Success(Unit)
            } finally {
                imapService.disconnect(store)
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to rename folder: ${e.message}")
        }
    }

    override suspend fun deleteFolder(folderId: Long): Result<Unit> {
        return try {
            // Get folder info
            val folderEntity = folderDao.getFolderById(folderId)
                ?: return Result.Error(Exception("Folder not found"))

            // Can't delete system folders
            if (folderEntity.type != FolderType.CUSTOM) {
                return Result.Error(Exception("Cannot delete system folders"))
            }

            // Get account and credentials
            val accountEntity = accountDao.getAccountById(folderEntity.accountId)
                ?: return Result.Error(Exception("Account not found"))

            val password = credentialManager.getPassword(folderEntity.accountId)
                ?: return Result.Error(Exception("Password not found"))

            val account = accountEntity.toDomain()

            // Connect to IMAP and delete folder
            val store = imapService.connect(account, password)

            try {
                val success = imapService.deleteFolderOnServer(
                    store = store,
                    folderName = folderEntity.name
                )

                if (!success) {
                    return Result.Error(Exception("Failed to delete folder on server. Make sure folder is empty."))
                }

                // Delete folder from local database (cascades to emails)
                folderDao.deleteFolderById(folderId)

                Result.Success(Unit)
            } finally {
                imapService.disconnect(store)
            }
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete folder: ${e.message}")
        }
    }
}

package com.emailclient.domain.repository

import com.emailclient.domain.model.Folder
import com.emailclient.domain.model.FolderType
import com.emailclient.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for folder operations
 */
interface FolderRepository {

    fun getFoldersByAccount(accountId: Long): Flow<List<Folder>>

    suspend fun getFolderById(folderId: Long): Result<Folder>

    suspend fun getFolderByName(accountId: Long, name: String): Result<Folder>

    suspend fun getFolderByType(accountId: Long, type: FolderType): Result<Folder>

    suspend fun syncFolders(accountId: Long): Result<Unit>

    suspend fun createFolder(accountId: Long, folderName: String): Result<Long>

    suspend fun renameFolder(folderId: Long, newName: String): Result<Unit>

    suspend fun deleteFolder(folderId: Long): Result<Unit>
}

package com.emailclient.data.local.dao

import androidx.room.*
import com.emailclient.data.local.entities.FolderEntity
import com.emailclient.domain.model.FolderType
import kotlinx.coroutines.flow.Flow

/**
 * DAO for folder operations
 */
@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY type ASC, displayName ASC")
    fun getFoldersByAccount(accountId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND name = :name")
    suspend fun getFolderByName(accountId: Long, name: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND type = :type LIMIT 1")
    suspend fun getFolderByType(accountId: Long, type: FolderType): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("UPDATE folders SET unreadCount = :count WHERE id = :folderId")
    suspend fun updateUnreadCount(folderId: Long, count: Int)

    @Query("UPDATE folders SET totalCount = :count WHERE id = :folderId")
    suspend fun updateTotalCount(folderId: Long, count: Int)

    @Query("UPDATE folders SET syncEnabled = :enabled WHERE id = :folderId")
    suspend fun setSyncEnabled(folderId: Long, enabled: Boolean)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Query("DELETE FROM folders WHERE accountId = :accountId")
    suspend fun deleteFoldersByAccount(accountId: Long)
}

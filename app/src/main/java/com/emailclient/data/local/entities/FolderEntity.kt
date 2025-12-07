package com.emailclient.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emailclient.domain.model.Folder
import com.emailclient.domain.model.FolderType

/**
 * Room entity for storing email folders
 */
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["accountId", "name"], unique = true)
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val name: String,
    val displayName: String,
    val type: FolderType,
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
    val syncEnabled: Boolean = true
)

/**
 * Extension function to convert FolderEntity to domain Folder model
 */
fun FolderEntity.toDomain(): Folder {
    return Folder(
        id = id,
        accountId = accountId,
        name = name,
        displayName = displayName,
        type = type,
        unreadCount = unreadCount,
        totalCount = totalCount,
        syncEnabled = syncEnabled
    )
}

/**
 * Extension function to convert domain Folder model to FolderEntity
 */
fun Folder.toEntity(): FolderEntity {
    return FolderEntity(
        id = id,
        accountId = accountId,
        name = name,
        displayName = displayName,
        type = type,
        unreadCount = unreadCount,
        totalCount = totalCount,
        syncEnabled = syncEnabled
    )
}

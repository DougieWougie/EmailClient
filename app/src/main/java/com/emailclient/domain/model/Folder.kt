package com.emailclient.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model representing an email folder
 */
@Parcelize
data class Folder(
    val id: Long = 0,
    val accountId: Long,
    val name: String,
    val displayName: String,
    val type: FolderType,
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
    val syncEnabled: Boolean = true
) : Parcelable

enum class FolderType {
    INBOX,
    SENT,
    DRAFTS,
    TRASH,
    SPAM,
    ARCHIVE,
    CUSTOM
}

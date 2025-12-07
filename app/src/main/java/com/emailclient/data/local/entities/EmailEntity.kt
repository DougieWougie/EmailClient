package com.emailclient.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emailclient.domain.model.Attachment
import com.emailclient.domain.model.Email
import com.emailclient.domain.model.EmailAddress
import java.util.Date

/**
 * Room entity for storing email messages
 */
@Entity(
    tableName = "emails",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["folderId"]),
        Index(value = ["messageId"], unique = true),
        Index(value = ["receivedDate"]),
        Index(value = ["isRead"])
    ]
)
data class EmailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val folderId: Long,
    val messageId: String,
    val from: EmailAddress,
    val to: List<EmailAddress>,
    val cc: List<EmailAddress> = emptyList(),
    val bcc: List<EmailAddress> = emptyList(),
    val subject: String,
    val body: String,
    val htmlBody: String? = null,
    val isHtml: Boolean = false,
    val receivedDate: Date,
    val sentDate: Date? = null,
    val isRead: Boolean = false,
    val isFlagged: Boolean = false,
    val hasAttachments: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val size: Long = 0,
    val snippet: String = ""
)

/**
 * Extension function to convert EmailEntity to domain Email model
 */
fun EmailEntity.toDomain(): Email {
    return Email(
        id = id.toString(),
        accountId = accountId,
        folderId = folderId,
        messageId = messageId,
        from = from,
        to = to,
        cc = cc,
        bcc = bcc,
        subject = subject,
        body = body,
        htmlBody = htmlBody,
        isHtml = isHtml,
        receivedDate = receivedDate,
        sentDate = sentDate,
        isRead = isRead,
        isFlagged = isFlagged,
        hasAttachments = hasAttachments,
        attachments = attachments,
        size = size,
        snippet = snippet
    )
}

/**
 * Extension function to convert domain Email model to EmailEntity
 */
fun Email.toEntity(): EmailEntity {
    return EmailEntity(
        id = id.toLongOrNull() ?: 0,
        accountId = accountId,
        folderId = folderId,
        messageId = messageId,
        from = from,
        to = to,
        cc = cc,
        bcc = bcc,
        subject = subject,
        body = body,
        htmlBody = htmlBody,
        isHtml = isHtml,
        receivedDate = receivedDate,
        sentDate = sentDate,
        isRead = isRead,
        isFlagged = isFlagged,
        hasAttachments = hasAttachments,
        attachments = attachments,
        size = size,
        snippet = snippet
    )
}

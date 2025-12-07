package com.emailclient.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Domain model representing an email message
 */
@Parcelize
data class Email(
    val id: String,
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
) : Parcelable {

    companion object {
        const val FOLDER_INBOX = "INBOX"
        const val FOLDER_SENT = "Sent"
        const val FOLDER_DRAFTS = "Drafts"
        const val FOLDER_TRASH = "Trash"
        const val FOLDER_SPAM = "Spam"
    }
}

@Parcelize
data class EmailAddress(
    val address: String,
    val personal: String? = null
) : Parcelable {

    val displayName: String
        get() = personal ?: address

    override fun toString(): String {
        return if (personal != null) {
            "$personal <$address>"
        } else {
            address
        }
    }
}

@Parcelize
data class Attachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val contentId: String? = null,
    val isInline: Boolean = false
) : Parcelable

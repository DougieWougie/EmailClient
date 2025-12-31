package com.emailclient.data.remote.imap

import com.emailclient.domain.model.Account
import com.emailclient.domain.model.Attachment
import com.emailclient.domain.model.AuthenticationType
import com.emailclient.domain.model.Email
import com.emailclient.domain.model.EmailAddress
import com.emailclient.domain.model.Folder as EmailFolder
import com.emailclient.domain.model.FolderType
import com.emailclient.domain.model.SecurityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart
import javax.mail.Folder as JavaMailFolder

/**
 * Service for IMAP email fetching using JavaMail
 */
@Singleton
class IMAPService @Inject constructor() {

    /**
     * Connect to IMAP server and return session
     * Supports both PASSWORD and OAUTH2 authentication
     */
    suspend fun connect(
        account: Account,
        password: String? = null,
        accessToken: String? = null
    ): Store = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.store.protocol", "imap")
                put("mail.imap.host", account.imapConfig.host)
                put("mail.imap.port", account.imapConfig.port.toString())

                when (account.imapConfig.securityType) {
                    SecurityType.SSL_TLS -> {
                        put("mail.imap.ssl.enable", "true")
                        put("mail.imap.ssl.protocols", "TLSv1.2 TLSv1.3")
                        put("mail.imap.ssl.checkserveridentity", "true")
                        // Security: Explicitly trust only the configured host
                        put("mail.imap.ssl.trust", account.imapConfig.host)
                    }
                    SecurityType.STARTTLS -> {
                        put("mail.imap.starttls.enable", "true")
                        put("mail.imap.starttls.required", "true")
                        put("mail.imap.ssl.protocols", "TLSv1.2 TLSv1.3")
                        put("mail.imap.ssl.checkserveridentity", "true")
                        // Security: Explicitly trust only the configured host
                        put("mail.imap.ssl.trust", account.imapConfig.host)
                    }
                    SecurityType.NONE -> {
                        throw SecurityException(
                            "Unencrypted connections are not allowed for security reasons. " +
                            "Please use SSL/TLS or STARTTLS."
                        )
                    }
                }

                // Connection settings - increased for slow/unreliable connections
                put("mail.imap.connectiontimeout", "90000") // 90 seconds
                put("mail.imap.timeout", "90000") // 90 seconds
                put("mail.imap.writetimeout", "90000") // 90 seconds

                // Authentication settings based on type
                when (account.imapConfig.authenticationType) {
                    AuthenticationType.OAUTH2 -> {
                        // XOAUTH2 SASL configuration for OAuth2
                        put("mail.imap.auth.mechanisms", "XOAUTH2")
                        put("mail.imap.sasl.enable", "true")
                        put("mail.imap.sasl.mechanisms", "XOAUTH2")
                        put("mail.imap.auth.plain.disable", "true")
                        put("mail.imap.auth.login.disable", "true")
                    }
                    AuthenticationType.PASSWORD -> {
                        // Prefer LOGIN over AUTHENTICATE PLAIN
                        // Some Dovecot servers have issues with AUTHENTICATE PLAIN
                        put("mail.imap.auth.login.disable", "false") // Enable LOGIN
                        put("mail.imap.auth.plain.disable", "true") // Disable PLAIN to force LOGIN
                    }
                    else -> {
                        // Default to PASSWORD authentication
                        put("mail.imap.auth.login.disable", "false")
                        put("mail.imap.auth.plain.disable", "true")
                    }
                }

                // Android-specific settings
                put("mail.imap.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.imap.ssl.socketFactory.fallback", "false")
            }

            val session = Session.getInstance(props)
            session.debug = false
            val store = session.getStore("imap")

            // Connect with appropriate authentication method
            when (account.imapConfig.authenticationType) {
                AuthenticationType.OAUTH2 -> {
                    requireNotNull(accessToken) { "Access token required for OAuth2 authentication" }
                    // OAuth2 XOAUTH2 SASL authentication string format:
                    // user=email\x01auth=Bearer token\x01\x01
                    val authString = "user=${account.email}\u0001auth=Bearer $accessToken\u0001\u0001"
                    store.connect(account.imapConfig.host, account.email, authString)
                }
                AuthenticationType.PASSWORD -> {
                    requireNotNull(password) { "Password required for PASSWORD authentication" }
                    store.connect(
                        account.imapConfig.host,
                        account.imapConfig.username,
                        password
                    )
                }
                else -> {
                    requireNotNull(password) { "Password required for authentication" }
                    store.connect(
                        account.imapConfig.host,
                        account.imapConfig.username,
                        password
                    )
                }
            }
            store
        } catch (e: AuthenticationFailedException) {
            android.util.Log.e("IMAPService", "✗ IMAP Authentication FAILED")
            android.util.Log.e("IMAPService", "Exception type: ${e.javaClass.simpleName}")
            android.util.Log.e("IMAPService", "Exception message: ${e.message}")
            android.util.Log.e("IMAPService", "Next exception: ${e.nextException?.message}")
            e.printStackTrace()
            throw Exception("IMAP Authentication failed. Please check your email and password.", e)
        } catch (e: MessagingException) {
            android.util.Log.e("IMAPService", "✗ IMAP MessagingException")
            android.util.Log.e("IMAPService", "Exception type: ${e.javaClass.simpleName}")
            android.util.Log.e("IMAPService", "Exception message: ${e.message}")
            android.util.Log.e("IMAPService", "Next exception: ${e.nextException?.message}")
            e.printStackTrace()
            val message = when {
                e.message?.contains("Unknown host") == true ->
                    "Cannot find IMAP server '${account.imapConfig.host}'. Please check the server address."
                e.message?.contains("Connection refused") == true || e.message?.contains("failed") == true ->
                    "Cannot connect to IMAP server '${account.imapConfig.host}:${account.imapConfig.port}'. Please check the server and port."
                e.message?.contains("timeout") == true ->
                    "Connection to IMAP server timed out. Please check your internet connection."
                else -> "IMAP connection failed: ${e.message ?: "Unknown error"}"
            }
            throw Exception(message, e)
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "✗ IMAP General Exception")
            android.util.Log.e("IMAPService", "Exception type: ${e.javaClass.simpleName}")
            android.util.Log.e("IMAPService", "Exception message: ${e.message}")
            e.printStackTrace()
            throw Exception("IMAP connection failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * Fetch folders from IMAP server
     */
    suspend fun fetchFolders(store: Store, accountId: Long): List<EmailFolder> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<EmailFolder>()

        val defaultFolder = store.defaultFolder
        val allFolders = defaultFolder.list("*")

        allFolders.forEach { imapFolder ->
            if ((imapFolder.type and JavaMailFolder.HOLDS_MESSAGES) != 0) {
                val folderType = determineFolderType(imapFolder.fullName)

                folders.add(
                    EmailFolder(
                        accountId = accountId,
                        name = imapFolder.fullName,
                        displayName = imapFolder.name,
                        type = folderType,
                        unreadCount = 0,
                        totalCount = 0,
                        syncEnabled = folderType in listOf(
                            FolderType.INBOX,
                            FolderType.SENT,
                            FolderType.DRAFTS
                        )
                    )
                )
            }
        }

        folders
    }

    /**
     * Fetch emails from a folder
     */
    suspend fun fetchEmails(
        store: Store,
        accountId: Long,
        folderId: Long,
        folderName: String,
        limit: Int = 50
    ): List<Email> = withContext(Dispatchers.IO) {
        val folder = store.getFolder(folderName)
        folder.open(JavaMailFolder.READ_ONLY)

        try {
            val messageCount = folder.messageCount
            if (messageCount == 0) {
                return@withContext emptyList()
            }

            // Fetch most recent messages
            val start = maxOf(1, messageCount - limit + 1)
            val messages = folder.getMessages(start, messageCount)

            // Fetch message data efficiently
            val fetchProfile = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(FetchProfile.Item.CONTENT_INFO)
                add("X-mailer")
            }
            folder.fetch(messages, fetchProfile)

            messages.reversed().mapNotNull { message ->
                parseMessage(message, accountId, folderId)
            }
        } finally {
            folder.close(false)
        }
    }

    /**
     * Parse a JavaMail message to our Email domain model
     */
    private fun parseMessage(message: Message, accountId: Long, folderId: Long): Email? {
        return try {
            val messageId = message.getHeader("Message-ID")?.firstOrNull()
                ?: UUID.randomUUID().toString()

            val from = (message.from?.firstOrNull() as? InternetAddress)?.let {
                EmailAddress(
                    address = it.address,
                    personal = it.personal
                )
            } ?: EmailAddress("unknown@unknown.com", "Unknown")

            val to = message.getRecipients(Message.RecipientType.TO)?.mapNotNull {
                (it as? InternetAddress)?.let { addr ->
                    EmailAddress(addr.address, addr.personal)
                }
            } ?: emptyList()

            val cc = message.getRecipients(Message.RecipientType.CC)?.mapNotNull {
                (it as? InternetAddress)?.let { addr ->
                    EmailAddress(addr.address, addr.personal)
                }
            } ?: emptyList()

            val subject = message.subject ?: "(No Subject)"

            val (body, htmlBody, isHtml) = extractBody(message)

            // Generate snippet - strip HTML tags if present
            val snippetText = if (containsHtmlTags(body)) {
                stripHtmlTags(body)
            } else {
                body
            }
            val snippet = snippetText.take(150).replace("\n", " ").trim()

            Email(
                id = "0", // Will be set by Room on insert
                accountId = accountId,
                folderId = folderId,
                messageId = messageId,
                from = from,
                to = to,
                cc = cc,
                subject = subject,
                body = body,
                htmlBody = htmlBody,
                isHtml = isHtml,
                receivedDate = message.receivedDate ?: Date(),
                sentDate = message.sentDate,
                isRead = message.flags.contains(Flags.Flag.SEEN),
                isFlagged = message.flags.contains(Flags.Flag.FLAGGED),
                hasAttachments = hasAttachments(message),
                attachments = extractAttachments(message),
                size = message.size.toLong(),
                snippet = snippet
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract text body from message
     */
    private fun extractBody(message: Message): Triple<String, String?, Boolean> {
        return try {
            val content = message.content

            when (content) {
                is String -> Triple(content, null, false)
                is MimeMultipart -> extractFromMultipart(content)
                else -> Triple("", null, false)
            }
        } catch (e: Exception) {
            Triple("Error loading message body", null, false)
        }
    }

    /**
     * Extract body from multipart message
     */
    private fun extractFromMultipart(multipart: MimeMultipart): Triple<String, String?, Boolean> {
        var textBody = ""
        var htmlBody: String? = null

        for (i in 0 until multipart.count) {
            val bodyPart = multipart.getBodyPart(i)
            val contentType = bodyPart.contentType.lowercase()

            when {
                contentType.contains("text/plain") -> {
                    textBody = bodyPart.content.toString()
                }
                contentType.contains("text/html") -> {
                    htmlBody = bodyPart.content.toString()
                }
                bodyPart.content is MimeMultipart -> {
                    val (text, html, _) = extractFromMultipart(bodyPart.content as MimeMultipart)
                    if (text.isNotEmpty()) textBody = text
                    if (html != null) htmlBody = html
                }
            }
        }

        val isHtml = htmlBody != null

        return Triple(textBody, htmlBody, isHtml)
    }

    /**
     * Check if message has attachments
     */
    private fun hasAttachments(message: Message): Boolean {
        return try {
            val content = message.content
            if (content is MimeMultipart) {
                for (i in 0 until content.count) {
                    val bodyPart = content.getBodyPart(i)
                    if (Part.ATTACHMENT.equals(bodyPart.disposition, ignoreCase = true)) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extract attachment metadata from message
     */
    private fun extractAttachments(message: Message): List<Attachment> {
        return try {
            val attachments = mutableListOf<Attachment>()
            val content = message.content

            if (content is MimeMultipart) {
                extractAttachmentsFromMultipart(content, attachments)
            }

            attachments
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Error extracting attachments", e)
            emptyList()
        }
    }

    /**
     * Recursively extract attachments from multipart content
     */
    private fun extractAttachmentsFromMultipart(
        multipart: MimeMultipart,
        attachments: MutableList<Attachment>
    ) {
        for (i in 0 until multipart.count) {
            val bodyPart = multipart.getBodyPart(i)
            val disposition = bodyPart.disposition?.lowercase()

            when {
                // Check for attachment disposition
                Part.ATTACHMENT.equals(disposition, ignoreCase = true) -> {
                    val attachment = createAttachment(bodyPart, i)
                    if (attachment != null) {
                        attachments.add(attachment)
                    }
                }
                // Check for inline disposition with filename (inline attachments/images)
                Part.INLINE.equals(disposition, ignoreCase = true) && bodyPart.fileName != null -> {
                    val attachment = createAttachment(bodyPart, i, isInline = true)
                    if (attachment != null) {
                        attachments.add(attachment)
                    }
                }
                // Recursively check nested multipart
                bodyPart.content is MimeMultipart -> {
                    extractAttachmentsFromMultipart(bodyPart.content as MimeMultipart, attachments)
                }
            }
        }
    }

    /**
     * Create attachment object from body part
     */
    private fun createAttachment(
        bodyPart: BodyPart,
        index: Int,
        isInline: Boolean = false
    ): Attachment? {
        return try {
            val fileName = bodyPart.fileName ?: "attachment_$index"
            val mimeType = bodyPart.contentType.split(";").firstOrNull()?.trim()
                ?: "application/octet-stream"
            val size = bodyPart.size.toLong()

            // Get Content-ID for inline attachments
            val contentId = if (isInline) {
                val cid = (bodyPart as? javax.mail.internet.MimeBodyPart)?.contentID
                cid?.removePrefix("<")?.removeSuffix(">")
            } else {
                null
            }

            Attachment(
                id = "$index", // Use index as ID, will be used to download
                fileName = fileName,
                mimeType = mimeType,
                size = size,
                contentId = contentId,
                isInline = isInline
            )
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Error creating attachment", e)
            null
        }
    }

    /**
     * Download attachment content from IMAP server
     */
    suspend fun downloadAttachment(
        store: Store,
        folderName: String,
        messageId: String,
        attachmentIndex: Int
    ): com.emailclient.util.Result<java.io.InputStream> = withContext(Dispatchers.IO) {
        return@withContext try {
            val folder = store.getFolder(folderName)
            folder.open(JavaMailFolder.READ_ONLY)

            try {
                // Find the message by Message-ID
                val message = findMessageByMessageId(folder, messageId)
                    ?: return@withContext com.emailclient.util.Result.Error(
                        Exception("Message not found: $messageId")
                    )

                // Get the content
                val content = message.content
                if (content !is MimeMultipart) {
                    return@withContext com.emailclient.util.Result.Error(
                        Exception("Message does not contain multipart content")
                    )
                }

                // Find the attachment by index
                val bodyPart = getAttachmentPart(content, attachmentIndex)
                    ?: return@withContext com.emailclient.util.Result.Error(
                        Exception("Attachment not found at index $attachmentIndex")
                    )

                // Get the input stream for the attachment
                val inputStream = bodyPart.inputStream

                com.emailclient.util.Result.Success(inputStream)
            } finally {
                // Don't close folder here - caller needs to read the stream
                // Caller is responsible for closing folder
            }
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Error downloading attachment", e)
            com.emailclient.util.Result.Error(e, "Failed to download attachment: ${e.message}")
        }
    }

    /**
     * Get attachment body part by index from multipart content
     */
    private fun getAttachmentPart(
        multipart: MimeMultipart,
        targetIndex: Int
    ): BodyPart? {
        var currentIndex = 0

        for (i in 0 until multipart.count) {
            val bodyPart = multipart.getBodyPart(i)
            val disposition = bodyPart.disposition?.lowercase()

            when {
                Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
                (Part.INLINE.equals(disposition, ignoreCase = true) && bodyPart.fileName != null) -> {
                    if (currentIndex == targetIndex) {
                        return bodyPart
                    }
                    currentIndex++
                }
                bodyPart.content is MimeMultipart -> {
                    val result = getAttachmentPart(bodyPart.content as MimeMultipart, targetIndex - currentIndex)
                    if (result != null) {
                        return result
                    }
                    // Update current index based on attachments found in nested multipart
                    currentIndex += countAttachments(bodyPart.content as MimeMultipart)
                }
            }
        }

        return null
    }

    /**
     * Count attachments in multipart content
     */
    private fun countAttachments(multipart: MimeMultipart): Int {
        var count = 0

        for (i in 0 until multipart.count) {
            val bodyPart = multipart.getBodyPart(i)
            val disposition = bodyPart.disposition?.lowercase()

            when {
                Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
                (Part.INLINE.equals(disposition, ignoreCase = true) && bodyPart.fileName != null) -> {
                    count++
                }
                bodyPart.content is MimeMultipart -> {
                    count += countAttachments(bodyPart.content as MimeMultipart)
                }
            }
        }

        return count
    }

    /**
     * Determine folder type from name
     */
    private fun determineFolderType(folderName: String): FolderType {
        val name = folderName.lowercase()
        return when {
            name.contains("inbox") -> FolderType.INBOX
            name.contains("sent") -> FolderType.SENT
            name.contains("draft") -> FolderType.DRAFTS
            name.contains("trash") || name.contains("deleted") -> FolderType.TRASH
            name.contains("spam") || name.contains("junk") -> FolderType.SPAM
            name.contains("archive") -> FolderType.ARCHIVE
            else -> FolderType.CUSTOM
        }
    }

    /**
     * Detect if content contains HTML tags (ReDoS-safe implementation)
     */
    private fun containsHtmlTags(content: String): Boolean {
        // Use simple string matching instead of complex regex to avoid ReDoS
        return content.contains("<div", ignoreCase = true) ||
               content.contains("<p>", ignoreCase = true) ||
               content.contains("<p ", ignoreCase = true) ||
               content.contains("<br", ignoreCase = true) ||
               content.contains("<span", ignoreCase = true) ||
               content.contains("<table", ignoreCase = true) ||
               content.contains("<html", ignoreCase = true) ||
               content.contains("<body", ignoreCase = true)
    }

    /**
     * Strip HTML tags from text to get clean preview
     */
    private fun stripHtmlTags(html: String): String {
        return html
            // Remove script and style tags with their content
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            // Replace <br> tags with spaces
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            // Replace </p> and </div> tags with spaces for better readability
            .replace(Regex("</(p|div)>", RegexOption.IGNORE_CASE), " ")
            // Remove all other HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Decode common HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            // Clean up multiple spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Disconnect from IMAP server
     */
    suspend fun disconnect(store: Store) = withContext(Dispatchers.IO) {
        if (store.isConnected) {
            store.close()
        }
    }

    /**
     * Create a new folder on the IMAP server
     */
    suspend fun createFolder(
        store: Store,
        folderName: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d("IMAPService", "Creating folder: $folderName")

            val folder = store.getFolder(folderName)

            if (folder.exists()) {
                android.util.Log.w("IMAPService", "Folder already exists: $folderName")
                return@withContext false
            }

            val success = folder.create(JavaMailFolder.HOLDS_MESSAGES)

            if (success) {
                android.util.Log.d("IMAPService", "✓ Folder created successfully: $folderName")
            } else {
                android.util.Log.e("IMAPService", "✗ Failed to create folder: $folderName")
            }

            success
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Error creating folder: $folderName", e)
            false
        }
    }

    /**
     * Rename a folder on the IMAP server
     */
    suspend fun renameFolder(
        store: Store,
        oldName: String,
        newName: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d("IMAPService", "Renaming folder: $oldName -> $newName")

            val oldFolder = store.getFolder(oldName)

            if (!oldFolder.exists()) {
                android.util.Log.e("IMAPService", "Source folder does not exist: $oldName")
                return@withContext false
            }

            val newFolder = store.getFolder(newName)

            if (newFolder.exists()) {
                android.util.Log.e("IMAPService", "Target folder already exists: $newName")
                return@withContext false
            }

            val success = oldFolder.renameTo(newFolder)

            if (success) {
                android.util.Log.d("IMAPService", "✓ Folder renamed successfully")
            } else {
                android.util.Log.e("IMAPService", "✗ Failed to rename folder")
            }

            success
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Error renaming folder", e)
            false
        }
    }

    /**
     * Delete a folder from the IMAP server
     */
    suspend fun deleteFolderOnServer(
        store: Store,
        folderName: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d("IMAPService", "Deleting folder: $folderName")

            val folder = store.getFolder(folderName)

            if (!folder.exists()) {
                android.util.Log.w("IMAPService", "Folder does not exist: $folderName")
                return@withContext false
            }

            // Close folder if it's open
            if (folder.isOpen) {
                folder.close(false)
            }

            val success = folder.delete(false)

            if (success) {
                android.util.Log.d("IMAPService", "✓ Folder deleted successfully")
            } else {
                android.util.Log.e("IMAPService", "✗ Failed to delete folder (may not be empty)")
            }

            success
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Error deleting folder", e)
            false
        }
    }

    /**
     * Mark email as read/unread on server
     */
    suspend fun setReadFlag(
        store: Store,
        folderName: String,
        messageId: String,
        isRead: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val folder = store.getFolder(folderName)
            folder.open(JavaMailFolder.READ_WRITE)

            try {
                val message = findMessageByMessageId(folder, messageId)
                if (message != null) {
                    message.setFlag(Flags.Flag.SEEN, isRead)
                    android.util.Log.d("IMAPService", "Marked message $messageId as ${if (isRead) "read" else "unread"}")
                    true
                } else {
                    android.util.Log.e("IMAPService", "Message not found: $messageId")
                    false
                }
            } finally {
                folder.close(false)
            }
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Failed to set read flag", e)
            false
        }
    }

    /**
     * Mark email as flagged/unflagged on server
     */
    suspend fun setFlaggedFlag(
        store: Store,
        folderName: String,
        messageId: String,
        isFlagged: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val folder = store.getFolder(folderName)
            folder.open(JavaMailFolder.READ_WRITE)

            try {
                val message = findMessageByMessageId(folder, messageId)
                if (message != null) {
                    message.setFlag(Flags.Flag.FLAGGED, isFlagged)
                    android.util.Log.d("IMAPService", "Marked message $messageId as ${if (isFlagged) "flagged" else "unflagged"}")
                    true
                } else {
                    android.util.Log.e("IMAPService", "Message not found: $messageId")
                    false
                }
            } finally {
                folder.close(false)
            }
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Failed to set flagged flag", e)
            false
        }
    }

    /**
     * Move email to another folder on server
     */
    suspend fun moveMessage(
        store: Store,
        sourceFolderName: String,
        targetFolderName: String,
        messageId: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val sourceFolder = store.getFolder(sourceFolderName)
            sourceFolder.open(JavaMailFolder.READ_WRITE)

            try {
                val message = findMessageByMessageId(sourceFolder, messageId)
                if (message != null) {
                    val targetFolder = store.getFolder(targetFolderName)

                    // Copy message to target folder
                    sourceFolder.copyMessages(arrayOf(message), targetFolder)

                    // Mark original as deleted
                    message.setFlag(Flags.Flag.DELETED, true)

                    // Expunge to actually delete
                    sourceFolder.expunge()

                    android.util.Log.d("IMAPService", "Moved message $messageId from $sourceFolderName to $targetFolderName")
                    true
                } else {
                    android.util.Log.e("IMAPService", "Message not found: $messageId")
                    false
                }
            } finally {
                sourceFolder.close(true)
            }
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Failed to move message", e)
            false
        }
    }

    /**
     * Delete email on server (mark as deleted and expunge)
     */
    suspend fun deleteMessage(
        store: Store,
        folderName: String,
        messageId: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val folder = store.getFolder(folderName)
            folder.open(JavaMailFolder.READ_WRITE)

            try {
                val message = findMessageByMessageId(folder, messageId)
                if (message != null) {
                    // Mark as deleted
                    message.setFlag(Flags.Flag.DELETED, true)

                    // Expunge to actually delete
                    folder.expunge()

                    android.util.Log.d("IMAPService", "Deleted message $messageId from $folderName")
                    true
                } else {
                    android.util.Log.e("IMAPService", "Message not found: $messageId")
                    false
                }
            } finally {
                folder.close(true)
            }
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Failed to delete message", e)
            false
        }
    }

    /**
     * Find a message in a folder by Message-ID header
     */
    private fun findMessageByMessageId(folder: JavaMailFolder, messageId: String): Message? {
        return try {
            // Search for message by Message-ID header
            val messages = folder.messages
            messages.find { message ->
                val msgId = message.getHeader("Message-ID")?.firstOrNull()
                msgId == messageId
            }
        } catch (e: Exception) {
            android.util.Log.e("IMAPService", "Error finding message by ID", e)
            null
        }
    }
}

package com.emailclient.data.remote.imap

import com.emailclient.domain.model.Account
import com.emailclient.domain.model.Attachment
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
     */
    suspend fun connect(
        account: Account,
        password: String
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
                        put("mail.imap.ssl.trust", "*")
                        put("mail.imap.ssl.checkserveridentity", "false")
                    }
                    SecurityType.STARTTLS -> {
                        put("mail.imap.starttls.enable", "true")
                        put("mail.imap.starttls.required", "true")
                        put("mail.imap.ssl.protocols", "TLSv1.2 TLSv1.3")
                        put("mail.imap.ssl.trust", "*")
                        put("mail.imap.ssl.checkserveridentity", "false")
                    }
                    SecurityType.NONE -> {
                        // No encryption
                    }
                }

                // Connection settings - increased for slow/unreliable connections
                put("mail.imap.connectiontimeout", "90000") // 90 seconds
                put("mail.imap.timeout", "90000") // 90 seconds
                put("mail.imap.writetimeout", "90000") // 90 seconds

                // Authentication settings - prefer LOGIN over AUTHENTICATE PLAIN
                // Some Dovecot servers have issues with AUTHENTICATE PLAIN
                put("mail.imap.auth.login.disable", "false") // Enable LOGIN
                put("mail.imap.auth.plain.disable", "true") // Disable PLAIN to force LOGIN

                // Android-specific settings
                put("mail.imap.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.imap.ssl.socketFactory.fallback", "false")
            }

            android.util.Log.d("IMAPService", "=== IMAP Connection Attempt ===")
            android.util.Log.d("IMAPService", "Host: ${account.imapConfig.host}")
            android.util.Log.d("IMAPService", "Port: ${account.imapConfig.port}")
            android.util.Log.d("IMAPService", "Username: ${account.imapConfig.username}")
            android.util.Log.d("IMAPService", "Security: ${account.imapConfig.securityType}")
            android.util.Log.d("IMAPService", "Auth Type: ${account.imapConfig.authenticationType}")
            android.util.Log.d("IMAPService", "Password Length: ${password.length} chars")

            val session = Session.getInstance(props)
            session.debug = true // Enable JavaMail debug output
            val store = session.getStore("imap")

            android.util.Log.d("IMAPService", "Attempting to connect to store...")
            store.connect(
                account.imapConfig.host,
                account.imapConfig.username,
                password
            )

            android.util.Log.d("IMAPService", "✓ IMAP Connection successful!")
            android.util.Log.d("IMAPService", "Store connected: ${store.isConnected}")
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
        val body = htmlBody ?: textBody

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
     * Detect if content contains HTML tags
     */
    private fun containsHtmlTags(content: String): Boolean {
        val htmlTagPattern = Regex(
            "<(div|p|br|span|a|img|table|tr|td|th|h[1-6]|ul|ol|li|strong|em|b|i|u|html|body|head)[\\s>]",
            RegexOption.IGNORE_CASE
        )
        return htmlTagPattern.containsMatchIn(content)
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
}

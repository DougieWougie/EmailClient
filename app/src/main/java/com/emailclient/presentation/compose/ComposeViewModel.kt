package com.emailclient.presentation.compose

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for composing emails
 */
@HiltViewModel
class ComposeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emailRepository: EmailRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComposeState>(ComposeState.Idle)
    val uiState: StateFlow<ComposeState> = _uiState.asStateFlow()

    private val _composeData = MutableStateFlow<ComposeData?>(null)
    val composeData: StateFlow<ComposeData?> = _composeData.asStateFlow()

    private val _attachments = MutableStateFlow<List<AttachmentItem>>(emptyList())
    val attachments: StateFlow<List<AttachmentItem>> = _attachments.asStateFlow()

    // Rate limiting
    private val sentTimes = mutableListOf<Long>()
    private val maxEmailsPer5Min = 20

    companion object {
        private const val MAX_ATTACHMENT_SIZE = 25 * 1024 * 1024L // 25 MB
        private const val MAX_TOTAL_ATTACHMENT_SIZE = 50 * 1024 * 1024L // 50 MB
    }

    /**
     * Prepare compose screen for reply or forward
     */
    fun prepareReplyOrForward(emailId: String, isReplyAll: Boolean, isForward: Boolean) {
        viewModelScope.launch {
            try {
                val result = emailRepository.getEmailById(emailId)
                if (result is Result.Success) {
                    val originalEmail = result.data

                    val to = when {
                        isForward -> ""
                        else -> originalEmail.from.address
                    }

                    val cc = when {
                        isReplyAll -> originalEmail.cc.joinToString(", ") { it.address }
                        else -> ""
                    }

                    val subject = when {
                        isForward -> "Fwd: ${originalEmail.subject}"
                        else -> "Re: ${originalEmail.subject.removePrefix("Re: ")}"
                    }

                    val body = buildReplyForwardBody(originalEmail, isForward)

                    _composeData.value = ComposeData(
                        to = to,
                        cc = cc,
                        subject = subject,
                        body = body
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ComposeViewModel", "Error preparing reply/forward", e)
            }
        }
    }

    /**
     * Build reply/forward body with quoted original message
     */
    private fun buildReplyForwardBody(originalEmail: com.emailclient.domain.model.Email, isForward: Boolean): String {
        val date = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())
            .format(originalEmail.receivedDate)

        val header = if (isForward) {
            "\n\n---------- Forwarded message ----------\n" +
                    "From: ${originalEmail.from.let { if (it.personal != null) "${it.personal} <${it.address}>" else it.address }}\n" +
                    "Date: $date\n" +
                    "Subject: ${originalEmail.subject}\n" +
                    "To: ${originalEmail.to.joinToString(", ") { if (it.personal != null) "${it.personal} <${it.address}>" else it.address }}\n\n"
        } else {
            "\n\nOn $date, ${originalEmail.from.let { if (it.personal != null) "${it.personal} <${it.address}>" else it.address }} wrote:\n> "
        }

        val quotedBody = if (isForward) {
            originalEmail.body
        } else {
            originalEmail.body.lines().joinToString("\n> ")
        }

        return header + quotedBody
    }

    /**
     * Send email
     */
    fun sendEmail(
        to: String,
        cc: String = "",
        bcc: String = "",
        subject: String,
        body: String,
        isHtml: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = ComposeState.Sending

            try {
                // Check rate limit
                if (!checkRateLimit()) {
                    _uiState.value = ComposeState.Error(
                        "Rate limit exceeded. You can send a maximum of $maxEmailsPer5Min emails per 5 minutes. Please wait before sending more emails."
                    )
                    return@launch
                }

                // Validate subject and body
                if (subject.length > 998) {
                    _uiState.value = ComposeState.Error("Subject too long (maximum 998 characters)")
                    return@launch
                }

                if (body.length > 10_000_000) {
                    _uiState.value = ComposeState.Error("Email body too large (maximum 10 MB)")
                    return@launch
                }

                // Check for null bytes and control characters
                if (subject.contains('\u0000') || body.contains('\u0000')) {
                    _uiState.value = ComposeState.Error("Email contains invalid characters")
                    return@launch
                }

                // Get the default account
                val accounts = accountRepository.getAllAccounts().first()
                if (accounts.isEmpty()) {
                    _uiState.value = ComposeState.Error("No accounts configured")
                    return@launch
                }

                val account = accounts.firstOrNull { it.isDefault } ?: accounts.first()

                // Parse email addresses
                val toAddresses = parseEmailAddresses(to)
                val ccAddresses = parseEmailAddresses(cc)
                val bccAddresses = parseEmailAddresses(bcc)

                if (toAddresses.isEmpty()) {
                    _uiState.value = ComposeState.Error("Please enter at least one recipient")
                    return@launch
                }

                // Get attachment URIs
                val attachmentUris = _attachments.value.map { it.uri }

                // Send email via repository
                val result = emailRepository.sendEmail(
                    accountId = account.id,
                    to = toAddresses,
                    cc = ccAddresses,
                    bcc = bccAddresses,
                    subject = subject,
                    body = body,
                    isHtml = isHtml,
                    attachmentUris = attachmentUris
                )

                when (result) {
                    is Result.Success -> {
                        _uiState.value = ComposeState.Success
                    }
                    is Result.Error -> {
                        _uiState.value = ComposeState.Error(
                            result.message ?: "Failed to send email"
                        )
                    }
                    else -> {
                        _uiState.value = ComposeState.Error("Unknown error occurred")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ComposeState.Error(
                    e.message ?: "Failed to send email"
                )
            }
        }
    }

    /**
     * Parse and validate comma-separated email addresses
     */
    private fun parseEmailAddresses(addresses: String): List<String> {
        if (addresses.isEmpty()) return emptyList()

        // Basic email regex pattern
        val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        return addresses.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .onEach { email ->
                // Check for email injection attempts
                if (email.contains("\n") || email.contains("\r") ||
                    email.contains("\t") || email.contains("\u0000")) {
                    throw SecurityException("Invalid email address: Email injection attempt detected")
                }

                // Validate email format
                if (!emailPattern.matches(email)) {
                    throw IllegalArgumentException("Invalid email address format: $email")
                }
            }
    }

    /**
     * Check rate limit for sending emails
     */
    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000)

        // Remove old entries
        sentTimes.removeAll { it < fiveMinutesAgo }

        if (sentTimes.size >= maxEmailsPer5Min) {
            return false
        }

        sentTimes.add(now)
        return true
    }

    fun resetState() {
        _uiState.value = ComposeState.Idle
    }

    /**
     * Add attachment from URI
     */
    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            try {
                // Get file metadata from URI
                val fileName = getFileNameFromUri(uri) ?: "attachment"
                val mimeType = getMimeTypeFromUri(uri) ?: "application/octet-stream"
                val size = getFileSizeFromUri(uri)

                // Validate size
                if (size > MAX_ATTACHMENT_SIZE) {
                    _uiState.value = ComposeState.Error("File too large (max 25 MB)")
                    return@launch
                }

                val currentSize = _attachments.value.sumOf { it.size }
                if (currentSize + size > MAX_TOTAL_ATTACHMENT_SIZE) {
                    _uiState.value = ComposeState.Error("Total attachment size exceeds 50 MB")
                    return@launch
                }

                // Validate file type
                if (!isAllowedFileType(fileName, mimeType)) {
                    _uiState.value = ComposeState.Error("File type not allowed")
                    return@launch
                }

                // Add to list
                val attachment = AttachmentItem(
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    size = size
                )
                _attachments.value = _attachments.value + attachment

            } catch (e: Exception) {
                _uiState.value = ComposeState.Error("Failed to add attachment: ${e.message}")
            }
        }
    }

    /**
     * Remove attachment by ID
     */
    fun removeAttachment(attachmentId: String) {
        _attachments.value = _attachments.value.filter { it.id != attachmentId }
    }

    /**
     * Get filename from URI
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }

    /**
     * Get MIME type from URI
     */
    private fun getMimeTypeFromUri(uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }

    /**
     * Get file size from URI
     */
    private fun getFileSizeFromUri(uri: Uri): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                cursor.getLong(sizeIndex)
            } else {
                0L
            }
        } ?: 0L
    }

    /**
     * Check if file type is allowed
     */
    private fun isAllowedFileType(fileName: String, mimeType: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        // Block dangerous file types
        val blockedExtensions = setOf(
            "exe", "bat", "sh", "app", "deb", "rpm", "apk",
            "msi", "dll", "scr", "vbs", "js", "jar", "com",
            "cmd", "ps1", "psm1"
        )

        if (extension in blockedExtensions) return false

        // Block executable MIME types
        val blockedMimeTypes = setOf(
            "application/x-executable",
            "application/x-msdownload",
            "application/x-sh",
            "application/x-bat"
        )

        if (mimeType in blockedMimeTypes) return false

        return true
    }
}

/**
 * UI state for compose screen
 */
sealed class ComposeState {
    object Idle : ComposeState()
    object Sending : ComposeState()
    object Success : ComposeState()
    data class Error(val message: String) : ComposeState()
}

/**
 * Data for pre-filling compose fields
 */
data class ComposeData(
    val to: String,
    val cc: String,
    val subject: String,
    val body: String
)

/**
 * Attachment item for compose screen
 */
data class AttachmentItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val size: Long
)

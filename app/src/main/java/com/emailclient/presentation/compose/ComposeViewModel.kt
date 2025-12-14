package com.emailclient.presentation.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for composing emails
 */
@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val emailRepository: EmailRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComposeState>(ComposeState.Idle)
    val uiState: StateFlow<ComposeState> = _uiState.asStateFlow()

    private val _composeData = MutableStateFlow<ComposeData?>(null)
    val composeData: StateFlow<ComposeData?> = _composeData.asStateFlow()

    // Rate limiting
    private val sentTimes = mutableListOf<Long>()
    private val maxEmailsPer5Min = 20

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

                // Send email via repository
                val result = emailRepository.sendEmail(
                    accountId = account.id,
                    to = toAddresses,
                    cc = ccAddresses,
                    bcc = bccAddresses,
                    subject = subject,
                    body = body,
                    isHtml = isHtml
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

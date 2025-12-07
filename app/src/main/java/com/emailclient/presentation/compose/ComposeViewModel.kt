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

    /**
     * Send email
     */
    fun sendEmail(
        to: String,
        cc: String = "",
        subject: String,
        body: String,
        isHtml: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = ComposeState.Sending

            try {
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

                if (toAddresses.isEmpty()) {
                    _uiState.value = ComposeState.Error("Please enter at least one recipient")
                    return@launch
                }

                // Send email via repository
                val result = emailRepository.sendEmail(
                    accountId = account.id,
                    to = toAddresses,
                    cc = ccAddresses,
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
     * Parse comma-separated email addresses
     */
    private fun parseEmailAddresses(addresses: String): List<String> {
        return addresses.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
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

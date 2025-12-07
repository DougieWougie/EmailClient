package com.emailclient.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.model.Account
import com.emailclient.domain.model.AccountType
import com.emailclient.domain.model.SecurityType
import com.emailclient.domain.model.ServerConfig
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.util.Result
import com.emailclient.util.WorkManagerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for account setup flow
 */
@HiltViewModel
class AccountSetupViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountSetupState>(AccountSetupState.Idle)
    val uiState: StateFlow<AccountSetupState> = _uiState.asStateFlow()

    /**
     * Test connection to email servers
     */
    fun testConnection(
        email: String,
        password: String,
        displayName: String,
        imapHost: String,
        imapPort: Int,
        imapSecurity: SecurityType,
        smtpHost: String,
        smtpPort: Int,
        smtpSecurity: SecurityType
    ) {
        viewModelScope.launch {
            _uiState.value = AccountSetupState.Testing

            val account = Account(
                email = email,
                displayName = displayName,
                accountType = AccountType.GENERIC,
                imapConfig = ServerConfig(
                    host = imapHost,
                    port = imapPort,
                    username = email,
                    securityType = imapSecurity
                ),
                smtpConfig = ServerConfig(
                    host = smtpHost,
                    port = smtpPort,
                    username = email,
                    securityType = smtpSecurity
                )
            )

            when (val result = accountRepository.testConnection(account, password)) {
                is Result.Success -> {
                    _uiState.value = AccountSetupState.TestSuccess(account, password)
                }
                is Result.Error -> {
                    _uiState.value = AccountSetupState.Error(
                        result.message ?: "Connection test failed"
                    )
                }
                else -> {
                    _uiState.value = AccountSetupState.Error("Unknown error occurred")
                }
            }
        }
    }

    /**
     * Add account after successful test
     */
    fun addAccount(account: Account, password: String) {
        viewModelScope.launch {
            _uiState.value = AccountSetupState.Adding

            when (val result = accountRepository.addAccount(account, password)) {
                is Result.Success -> {
                    // Schedule background sync
                    workManagerHelper.schedulePeriodicSync()

                    _uiState.value = AccountSetupState.Success(result.data)
                }
                is Result.Error -> {
                    _uiState.value = AccountSetupState.Error(
                        result.message ?: "Failed to add account"
                    )
                }
                else -> {
                    _uiState.value = AccountSetupState.Error("Unknown error occurred")
                }
            }
        }
    }

    /**
     * Quick setup with common provider settings
     */
    fun quickSetup(email: String, password: String, displayName: String, provider: EmailProvider) {
        val (imapHost, imapPort, imapSecurity, smtpHost, smtpPort, smtpSecurity) = when (provider) {
            EmailProvider.GMAIL -> Tuple6(
                "imap.gmail.com", 993, SecurityType.SSL_TLS,
                "smtp.gmail.com", 465, SecurityType.SSL_TLS
            )
            EmailProvider.OUTLOOK -> Tuple6(
                "outlook.office365.com", 993, SecurityType.SSL_TLS,
                "smtp.office365.com", 587, SecurityType.STARTTLS
            )
            EmailProvider.YAHOO -> Tuple6(
                "imap.mail.yahoo.com", 993, SecurityType.SSL_TLS,
                "smtp.mail.yahoo.com", 465, SecurityType.SSL_TLS
            )
        }

        testConnection(
            email, password, displayName,
            imapHost, imapPort, imapSecurity,
            smtpHost, smtpPort, smtpSecurity
        )
    }

    fun resetState() {
        _uiState.value = AccountSetupState.Idle
    }
}

/**
 * UI state for account setup
 */
sealed class AccountSetupState {
    object Idle : AccountSetupState()
    object Testing : AccountSetupState()
    data class TestSuccess(val account: Account, val password: String) : AccountSetupState()
    object Adding : AccountSetupState()
    data class Success(val accountId: Long) : AccountSetupState()
    data class Error(val message: String) : AccountSetupState()
}

/**
 * Common email providers
 */
enum class EmailProvider {
    GMAIL, OUTLOOK, YAHOO
}

/**
 * Helper for returning 6 values
 */
private data class Tuple6<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

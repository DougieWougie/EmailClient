package com.emailclient.presentation.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.data.local.ProfileImageManager
import com.emailclient.data.remote.AutoDiscoveryService
import com.emailclient.domain.model.Account
import com.emailclient.domain.model.AccountType
import com.emailclient.domain.model.AuthenticationType
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
    private val workManagerHelper: WorkManagerHelper,
    private val autoDiscoveryService: AutoDiscoveryService,
    private val profileImageManager: ProfileImageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountSetupState>(AccountSetupState.Idle)
    val uiState: StateFlow<AccountSetupState> = _uiState.asStateFlow()

    private val _discoveredConfig = MutableStateFlow<AutoDiscoveryService.DiscoveredConfig?>(null)
    val discoveredConfig: StateFlow<AutoDiscoveryService.DiscoveredConfig?> = _discoveredConfig.asStateFlow()

    private val _editingAccount = MutableStateFlow<Account?>(null)
    val editingAccount: StateFlow<Account?> = _editingAccount.asStateFlow()

    private val _editingAccountPassword = MutableStateFlow<String?>(null)
    val editingAccountPassword: StateFlow<String?> = _editingAccountPassword.asStateFlow()

    private var profileImageUri: String? = null
    private var pendingImageUri: Uri? = null

    val isEditMode: Boolean
        get() = _editingAccount.value != null

    /**
     * Set profile image URI from image picker.
     * The image will be securely saved when the account is created/updated.
     */
    fun setProfileImage(uri: String?) {
        pendingImageUri = uri?.let { Uri.parse(it) }
    }

    /**
     * Load account for editing
     */
    fun loadAccountForEdit(accountId: Long) {
        viewModelScope.launch {
            when (val result = accountRepository.getAccountById(accountId)) {
                is Result.Success -> {
                    _editingAccount.value = result.data
                    profileImageUri = result.data.profileImageUri
                    // Load password for editing
                    _editingAccountPassword.value = accountRepository.getPassword(accountId)
                }
                is Result.Error -> {
                    _uiState.value = AccountSetupState.Error(
                        result.message ?: "Failed to load account"
                    )
                }
                else -> {
                    _uiState.value = AccountSetupState.Error("Unknown error occurred")
                }
            }
        }
    }

    /**
     * Discover email server settings automatically
     */
    fun discoverSettings(email: String) {
        viewModelScope.launch {
            _uiState.value = AccountSetupState.Discovering

            val config = autoDiscoveryService.discoverSettings(email)
            _discoveredConfig.value = config

            if (config != null) {
                android.util.Log.d("AccountSetup", "Auto-discovery successful: ${config.provider} via ${config.source}")
                _uiState.value = AccountSetupState.DiscoverySuccess(config)
            } else {
                android.util.Log.d("AccountSetup", "Auto-discovery failed, manual configuration required")
                _uiState.value = AccountSetupState.DiscoveryFailed
            }
        }
    }

    /**
     * Test connection with discovered settings
     */
    fun testDiscoveredConnection(
        email: String,
        password: String,
        displayName: String,
        config: AutoDiscoveryService.DiscoveredConfig
    ) {
        testConnection(
            email = email,
            password = password,
            displayName = displayName,
            imapHost = config.imapConfig.host,
            imapPort = config.imapConfig.port,
            imapSecurity = config.imapConfig.securityType,
            smtpHost = config.smtpConfig.host,
            smtpPort = config.smtpConfig.port,
            smtpSecurity = config.smtpConfig.securityType
        )
    }

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

            android.util.Log.d("AccountSetup", "=== Testing Connection ===")
            android.util.Log.d("AccountSetup", "Email: $email")
            android.util.Log.d("AccountSetup", "Display Name: $displayName")
            android.util.Log.d("AccountSetup", "Password Length: ${password.length} chars")
            android.util.Log.d("AccountSetup", "IMAP: $imapHost:$imapPort ($imapSecurity)")
            android.util.Log.d("AccountSetup", "SMTP: $smtpHost:$smtpPort ($smtpSecurity)")

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
                ),
                profileImageUri = profileImageUri
            )

            when (val result = accountRepository.testConnection(account, password)) {
                is Result.Success -> {
                    android.util.Log.d("AccountSetup", "✓ Connection test SUCCESSFUL!")
                    _uiState.value = AccountSetupState.TestSuccess(account, password)
                }
                is Result.Error -> {
                    // Try message first, then exception message, then generic
                    val errorMessage = result.message
                        ?: result.exception.message
                        ?: "Connection test failed - please check your settings"

                    android.util.Log.e("AccountSetup", "✗ Connection test FAILED: $errorMessage", result.exception)
                    _uiState.value = AccountSetupState.Error(errorMessage)
                }
                else -> {
                    _uiState.value = AccountSetupState.Error("Unknown error occurred")
                }
            }
        }
    }

    /**
     * Add or update account after successful test
     */
    fun addAccount(account: Account, password: String) {
        viewModelScope.launch {
            _uiState.value = AccountSetupState.Adding

            val editingAccountValue = _editingAccount.value

            if (editingAccountValue != null) {
                // Edit mode: Update existing account

                // Save profile image securely if a new one was selected
                val savedImageUri = if (pendingImageUri != null) {
                    when (val result = profileImageManager.saveProfileImage(
                        pendingImageUri!!,
                        editingAccountValue.id
                    )) {
                        is ProfileImageManager.Result.Success -> {
                            // Clean up old images
                            profileImageManager.cleanupOldImages(
                                editingAccountValue.id,
                                result.imageUri
                            )
                            result.imageUri
                        }
                        is ProfileImageManager.Result.Error -> {
                            _uiState.value = AccountSetupState.Error(result.message)
                            return@launch
                        }
                    }
                } else {
                    editingAccountValue.profileImageUri
                }

                val updatedAccount = account.copy(
                    id = editingAccountValue.id,
                    isDefault = editingAccountValue.isDefault,
                    syncEnabled = editingAccountValue.syncEnabled,
                    profileImageUri = savedImageUri
                )

                when (val result = accountRepository.updateAccount(updatedAccount)) {
                    is Result.Success -> {
                        // Update password in credential manager
                        accountRepository.updatePassword(updatedAccount.id, password)
                        _uiState.value = AccountSetupState.Success(updatedAccount.id)
                    }
                    is Result.Error -> {
                        _uiState.value = AccountSetupState.Error(
                            result.message ?: "Failed to update account"
                        )
                    }
                    else -> {
                        _uiState.value = AccountSetupState.Error("Unknown error occurred")
                    }
                }
            } else {
                // Create mode: Add new account
                when (val addResult = accountRepository.addAccount(account, password)) {
                    is Result.Success -> {
                        val accountId = addResult.data

                        // Save profile image securely if one was selected
                        if (pendingImageUri != null) {
                            when (val imageResult = profileImageManager.saveProfileImage(
                                pendingImageUri!!,
                                accountId
                            )) {
                                is ProfileImageManager.Result.Success -> {
                                    // Update account with saved image URI
                                    val updatedAccount = account.copy(
                                        id = accountId,
                                        profileImageUri = imageResult.imageUri
                                    )
                                    accountRepository.updateAccount(updatedAccount)
                                }
                                is ProfileImageManager.Result.Error -> {
                                    // Log error but don't fail account creation
                                    android.util.Log.e("AccountSetup",
                                        "Failed to save profile image: ${imageResult.message}")
                                }
                            }
                        }

                        // Schedule background sync
                        workManagerHelper.schedulePeriodicSync()

                        _uiState.value = AccountSetupState.Success(accountId)
                    }
                    is Result.Error -> {
                        _uiState.value = AccountSetupState.Error(
                            addResult.message ?: "Failed to add account"
                        )
                    }
                    else -> {
                        _uiState.value = AccountSetupState.Error("Unknown error occurred")
                    }
                }
            }
        }
    }

    /**
     * Add OAuth2 account (for Microsoft Outlook/Office 365)
     * Called after successful OAuth2 authentication
     */
    fun addOAuth2Account(
        email: String,
        displayName: String,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
        config: AutoDiscoveryService.DiscoveredConfig? = null
    ) {
        viewModelScope.launch {
            _uiState.value = AccountSetupState.Adding

            android.util.Log.d("AccountSetup", "=== Adding OAuth2 Account ===")
            android.util.Log.d("AccountSetup", "Email: $email")
            android.util.Log.d("AccountSetup", "Display Name: $displayName")
            android.util.Log.d("AccountSetup", "Token expires at: $expiresAt")

            // Use discovered config if available, otherwise use default Outlook settings
            val finalConfig = config ?: AutoDiscoveryService.DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "outlook.office365.com",
                    port = 993,
                    username = email,
                    securityType = SecurityType.SSL_TLS,
                    authenticationType = AuthenticationType.OAUTH2
                ),
                smtpConfig = ServerConfig(
                    host = "smtp.office365.com",
                    port = 587,
                    username = email,
                    securityType = SecurityType.STARTTLS,
                    authenticationType = AuthenticationType.OAUTH2
                ),
                provider = "Outlook",
                source = AutoDiscoveryService.DiscoverySource.KNOWN_PROVIDER,
                supportsOAuth2 = true,
                recommendedAuthType = AuthenticationType.OAUTH2
            )

            val account = Account(
                email = email,
                displayName = displayName,
                accountType = AccountType.OUTLOOK,
                imapConfig = finalConfig.imapConfig,
                smtpConfig = finalConfig.smtpConfig,
                profileImageUri = profileImageUri
            )

            when (val addResult = accountRepository.addOAuth2Account(
                account = account,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt
            )) {
                is Result.Success -> {
                    val accountId = addResult.data

                    // Save profile image securely if one was selected
                    if (pendingImageUri != null) {
                        when (val imageResult = profileImageManager.saveProfileImage(
                            pendingImageUri!!,
                            accountId
                        )) {
                            is ProfileImageManager.Result.Success -> {
                                // Update account with saved image URI
                                val updatedAccount = account.copy(
                                    id = accountId,
                                    profileImageUri = imageResult.imageUri
                                )
                                accountRepository.updateAccount(updatedAccount)
                            }
                            is ProfileImageManager.Result.Error -> {
                                // Log error but don't fail account creation
                                android.util.Log.e("AccountSetup",
                                    "Failed to save profile image: ${imageResult.message}")
                            }
                        }
                    }

                    // Schedule background sync
                    workManagerHelper.schedulePeriodicSync()

                    // Schedule OAuth2 token refresh
                    workManagerHelper.scheduleTokenRefresh()

                    android.util.Log.d("AccountSetup", "✓ OAuth2 account added successfully! Account ID: $accountId")
                    _uiState.value = AccountSetupState.Success(accountId)
                }
                is Result.Error -> {
                    android.util.Log.e("AccountSetup", "✗ Failed to add OAuth2 account: ${addResult.message}", addResult.exception)
                    _uiState.value = AccountSetupState.Error(
                        addResult.message ?: "Failed to add OAuth2 account"
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
    object Discovering : AccountSetupState()
    data class DiscoverySuccess(val config: AutoDiscoveryService.DiscoveredConfig) : AccountSetupState()
    object DiscoveryFailed : AccountSetupState()
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

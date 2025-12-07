package com.emailclient.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.model.Email
import com.emailclient.domain.model.FolderType
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.domain.repository.FolderRepository
import com.emailclient.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Inbox screen
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val emailRepository: EmailRepository,
    private val accountRepository: AccountRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _emails = MutableStateFlow<List<Email>>(emptyList())
    val emails: StateFlow<List<Email>> = _emails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentAccountId = MutableStateFlow<Long?>(null)
    private val _currentFolderId = MutableStateFlow<Long?>(null)

    init {
        loadEmails()
    }

    fun loadEmails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Get the default account
                val accounts = accountRepository.getAllAccounts().first()
                if (accounts.isEmpty()) {
                    _error.value = "No accounts configured"
                    _isLoading.value = false
                    return@launch
                }

                val account = accounts.firstOrNull { it.isDefault } ?: accounts.first()
                _currentAccountId.value = account.id

                // Get the inbox folder for that account
                val inboxResult = folderRepository.getFolderByType(account.id, FolderType.INBOX)
                when (inboxResult) {
                    is Result.Success -> {
                        val inbox = inboxResult.data
                        _currentFolderId.value = inbox.id

                        // Load emails from that folder
                        emailRepository.getEmailsByFolder(inbox.id).collect { emails ->
                            _emails.value = emails
                        }
                    }
                    is Result.Error -> {
                        _error.value = "Inbox folder not found. Try syncing your account."
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load emails"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshEmails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val accountId = _currentAccountId.value
                val folderId = _currentFolderId.value

                if (accountId == null || folderId == null) {
                    _error.value = "No account or folder selected"
                    _isLoading.value = false
                    return@launch
                }

                // Trigger email sync from server
                val result = emailRepository.syncEmails(accountId, folderId)
                when (result) {
                    is Result.Success -> {
                        // Emails will be updated automatically via the Flow
                    }
                    is Result.Error -> {
                        _error.value = result.message ?: "Failed to sync emails"
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to refresh emails"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAsRead(emailId: String) {
        viewModelScope.launch {
            emailRepository.markAsRead(emailId, true)
        }
    }

    fun deleteEmail(emailId: String) {
        viewModelScope.launch {
            emailRepository.deleteEmail(emailId)
        }
    }
}

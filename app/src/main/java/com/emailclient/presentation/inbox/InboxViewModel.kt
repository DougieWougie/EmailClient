package com.emailclient.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.model.Email
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        loadEmails()
    }

    fun loadEmails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // TODO: Get current account and folder
                // For now, this is a placeholder - actual implementation will:
                // 1. Get the default account
                // 2. Get the inbox folder for that account
                // 3. Load emails from that folder
                // emailRepository.getEmailsByFolder(folderId).collect { emails ->
                //     _emails.value = emails
                // }
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
                // TODO: Trigger email sync from server
                // val result = emailRepository.syncEmails(accountId, folderId)
                loadEmails()
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

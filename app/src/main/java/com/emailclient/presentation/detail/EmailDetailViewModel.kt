package com.emailclient.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.model.Email
import com.emailclient.domain.model.Folder
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.domain.repository.FolderRepository
import com.emailclient.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for email detail screen
 */
@HiltViewModel
class EmailDetailViewModel @Inject constructor(
    private val emailRepository: EmailRepository,
    private val folderRepository: FolderRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _email = MutableStateFlow<Email?>(null)
    val email: StateFlow<Email?> = _email.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _actionResult = MutableStateFlow<String?>(null)
    val actionResult: StateFlow<String?> = _actionResult.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _imagesLoaded = MutableStateFlow(false)
    val imagesLoaded: StateFlow<Boolean> = _imagesLoaded.asStateFlow()

    private val _shouldShowLoadImagesButton = MutableStateFlow(false)
    val shouldShowLoadImagesButton: StateFlow<Boolean> = _shouldShowLoadImagesButton.asStateFlow()

    private val _autoDownloadImagesEnabled = MutableStateFlow(false)
    val autoDownloadImagesEnabled: StateFlow<Boolean> = _autoDownloadImagesEnabled.asStateFlow()

    private var pendingAction: PendingAction? = null

    data class PendingAction(
        val emailId: String,
        val originalFolderId: Long,
        val actionType: ActionType
    )

    enum class ActionType { DELETE, ARCHIVE }

    /**
     * Load email by ID
     */
    fun loadEmail(emailId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _imagesLoaded.value = false  // Reset image loading state

            try {
                when (val result = emailRepository.getEmailById(emailId)) {
                    is Result.Success -> {
                        _email.value = result.data

                        // Mark as read
                        if (!result.data.isRead) {
                            emailRepository.markAsRead(emailId, true)
                        }

                        // Load account settings to determine auto-download preference
                        loadAccountSettings(result.data.accountId)
                    }
                    is Result.Error -> {
                        _error.value = result.message ?: "Failed to load email"
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load email"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Delete email by ID
     */
    fun deleteEmail(emailId: String) {
        viewModelScope.launch {
            // Store original folder ID for undo
            val email = _email.value
            if (email != null) {
                pendingAction = PendingAction(
                    emailId = emailId,
                    originalFolderId = email.folderId,
                    actionType = ActionType.DELETE
                )
            }

            when (val result = emailRepository.deleteEmail(emailId)) {
                is Result.Success -> {
                    _actionResult.value = "Email deleted"
                }
                is Result.Error -> {
                    _error.value = result.message ?: "Failed to delete email"
                    pendingAction = null // Clear pending action on error
                }
                else -> {
                    _error.value = "Unknown error occurred"
                    pendingAction = null
                }
            }
        }
    }

    /**
     * Archive email by ID
     */
    fun archiveEmail(emailId: String) {
        viewModelScope.launch {
            // Store original folder ID for undo
            val email = _email.value
            if (email != null) {
                pendingAction = PendingAction(
                    emailId = emailId,
                    originalFolderId = email.folderId,
                    actionType = ActionType.ARCHIVE
                )
            }

            when (val result = emailRepository.archiveEmail(emailId)) {
                is Result.Success -> {
                    _actionResult.value = "Email archived"
                }
                is Result.Error -> {
                    _error.value = result.message ?: "Failed to archive email"
                    pendingAction = null // Clear pending action on error
                }
                else -> {
                    _error.value = "Unknown error occurred"
                    pendingAction = null
                }
            }
        }
    }

    /**
     * Move email to a different folder
     */
    fun moveEmail(emailId: String, folderId: Long) {
        viewModelScope.launch {
            when (val result = emailRepository.moveToFolder(emailId, folderId)) {
                is Result.Success -> {
                    _actionResult.value = "Email moved"
                }
                is Result.Error -> {
                    _error.value = result.message ?: "Failed to move email"
                }
                else -> {
                    _error.value = "Unknown error occurred"
                }
            }
        }
    }

    /**
     * Load folders for current account
     */
    fun loadFolders() {
        viewModelScope.launch {
            try {
                when (val result = accountRepository.getDefaultAccount()) {
                    is Result.Success -> {
                        val account = result.data
                        folderRepository.getFoldersByAccount(account.id).collect { folderList ->
                            _folders.value = folderList
                        }
                    }
                    is Result.Error -> {
                        _error.value = result.message ?: "Failed to load folders"
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load folders"
            }
        }
    }

    fun clearActionResult() {
        _actionResult.value = null
    }

    /**
     * Undo the last delete or archive action
     */
    fun undoLastAction() {
        viewModelScope.launch {
            pendingAction?.let { action ->
                when (val result = emailRepository.moveToFolder(action.emailId, action.originalFolderId)) {
                    is Result.Success -> {
                        _actionResult.value = "Action undone"
                    }
                    is Result.Error -> {
                        _error.value = result.message ?: "Failed to undo action"
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                    }
                }
                pendingAction = null
            }
        }
    }

    /**
     * Finalize the pending action (make it permanent)
     */
    fun finalizeAction() {
        pendingAction = null
    }

    /**
     * Load account settings to determine auto-download preference
     */
    private suspend fun loadAccountSettings(accountId: Long) {
        when (val result = accountRepository.getAccountById(accountId)) {
            is Result.Success -> {
                _autoDownloadImagesEnabled.value = result.data.autoDownloadImages
                updateLoadImagesButtonVisibility()
            }
            is Result.Error -> {
                // Default to not auto-downloading if we can't fetch settings
                _autoDownloadImagesEnabled.value = false
                updateLoadImagesButtonVisibility()
            }
            else -> {
                _autoDownloadImagesEnabled.value = false
                updateLoadImagesButtonVisibility()
            }
        }
    }

    /**
     * Determine whether to show the Load Images button
     */
    private fun updateLoadImagesButtonVisibility() {
        val email = _email.value
        val hasExternalImages = email?.let { hasExternalImages(it) } ?: false

        _shouldShowLoadImagesButton.value =
            hasExternalImages &&
            !_autoDownloadImagesEnabled.value &&
            !_imagesLoaded.value
    }

    /**
     * Check if email contains external images
     */
    private fun hasExternalImages(email: Email): Boolean {
        // Only check HTML emails
        if (!email.isHtml && email.htmlBody == null) {
            return false
        }

        val content = email.htmlBody ?: email.body

        // Check for img tags with http/https src (external images)
        // Using simple string matching to avoid ReDoS
        return content.contains("<img", ignoreCase = true) &&
               (content.contains("src=\"http://", ignoreCase = true) ||
                content.contains("src=\"https://", ignoreCase = true) ||
                content.contains("src='http://", ignoreCase = true) ||
                content.contains("src='https://", ignoreCase = true))
    }

    /**
     * Load images for current email (temporary, session only)
     */
    fun loadImages() {
        _imagesLoaded.value = true
        updateLoadImagesButtonVisibility()
    }
}

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

            try {
                when (val result = emailRepository.getEmailById(emailId)) {
                    is Result.Success -> {
                        _email.value = result.data

                        // Mark as read
                        if (!result.data.isRead) {
                            emailRepository.markAsRead(emailId, true)
                        }
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
}

package com.emailclient.presentation.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.data.local.AppPreferences
import com.emailclient.domain.model.Email
import com.emailclient.domain.model.Folder
import com.emailclient.domain.model.SwipeAction
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.domain.repository.FolderRepository
import com.emailclient.presentation.common.PendingSwipeAction
import com.emailclient.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for generic folder view
 */
@HiltViewModel
class FolderViewViewModel @Inject constructor(
    private val emailRepository: EmailRepository,
    private val folderRepository: FolderRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val folderId: Long = savedStateHandle["folderId"]
        ?: throw IllegalArgumentException("folderId is required")

    private val _folder = MutableStateFlow<Folder?>(null)
    val folder: StateFlow<Folder?> = _folder.asStateFlow()

    private val _emails = MutableStateFlow<List<Email>>(emptyList())
    val emails: StateFlow<List<Email>> = _emails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Selection mode state
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedEmailIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedEmailIds: StateFlow<Set<String>> = _selectedEmailIds.asStateFlow()

    private val _isBulkOperationInProgress = MutableStateFlow(false)
    val isBulkOperationInProgress: StateFlow<Boolean> = _isBulkOperationInProgress.asStateFlow()

    // Swipe action state
    private var pendingSwipeAction: PendingSwipeAction? = null

    private val _swipeActionResult = MutableStateFlow<String?>(null)
    val swipeActionResult: StateFlow<String?> = _swipeActionResult.asStateFlow()

    init {
        loadFolder()
        loadEmails()
    }

    private fun loadFolder() {
        viewModelScope.launch {
            when (val result = folderRepository.getFolderById(folderId)) {
                is Result.Success -> _folder.value = result.data
                is Result.Error -> _error.value = "Failed to load folder"
                else -> _error.value = "Unknown error occurred"
            }
        }
    }

    fun loadEmails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Load emails from the specified folder
                emailRepository.getEmailsByFolder(folderId).collect { emails ->
                    _emails.value = emails
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load emails"
                _isLoading.value = false
            }
        }
    }

    fun refreshEmails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val folder = _folder.value
                if (folder == null) {
                    _error.value = "Folder not found"
                    _isLoading.value = false
                    return@launch
                }

                // Trigger email sync from server
                val result = emailRepository.syncEmails(folder.accountId, folderId)
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

    // Selection mode management
    fun enterSelectionMode(initialEmailId: String) {
        _isSelectionMode.value = true
        _selectedEmailIds.value = setOf(initialEmailId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedEmailIds.value = emptySet()
    }

    fun toggleEmailSelection(emailId: String) {
        val currentSelection = _selectedEmailIds.value.toMutableSet()
        if (currentSelection.contains(emailId)) {
            currentSelection.remove(emailId)
        } else {
            currentSelection.add(emailId)
        }
        _selectedEmailIds.value = currentSelection

        // Auto-exit selection mode if no items selected
        if (currentSelection.isEmpty()) {
            exitSelectionMode()
        }
    }

    fun selectAllEmails() {
        _selectedEmailIds.value = _emails.value.map { it.id }.toSet()
    }

    fun deselectAllEmails() {
        _selectedEmailIds.value = emptySet()
        exitSelectionMode()
    }

    fun isEmailSelected(emailId: String): Boolean {
        return _selectedEmailIds.value.contains(emailId)
    }

    fun getSelectedCount(): Int = _selectedEmailIds.value.size

    fun getCurrentAccountId(): Long? = _folder.value?.accountId

    // Bulk operations
    fun bulkMarkAsRead(read: Boolean) {
        viewModelScope.launch {
            _isBulkOperationInProgress.value = true
            _error.value = null

            try {
                val selectedIds = _selectedEmailIds.value.toList()
                var successCount = 0
                var errorCount = 0

                selectedIds.forEach { emailId ->
                    when (emailRepository.markAsRead(emailId, read)) {
                        is Result.Success -> successCount++
                        is Result.Error -> errorCount++
                        else -> {}
                    }
                }

                if (errorCount > 0) {
                    _error.value = "Failed to update $errorCount email(s)"
                }

                exitSelectionMode()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to mark emails"
            } finally {
                _isBulkOperationInProgress.value = false
            }
        }
    }

    fun bulkDelete() {
        viewModelScope.launch {
            _isBulkOperationInProgress.value = true
            _error.value = null

            try {
                val selectedIds = _selectedEmailIds.value.toList()
                var successCount = 0
                var errorCount = 0

                selectedIds.forEach { emailId ->
                    when (emailRepository.deleteEmail(emailId)) {
                        is Result.Success -> successCount++
                        is Result.Error -> errorCount++
                        else -> {}
                    }
                }

                if (errorCount > 0) {
                    _error.value = "Failed to delete $errorCount email(s)"
                }

                exitSelectionMode()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete emails"
            } finally {
                _isBulkOperationInProgress.value = false
            }
        }
    }

    fun bulkArchive() {
        viewModelScope.launch {
            _isBulkOperationInProgress.value = true
            _error.value = null

            try {
                val selectedIds = _selectedEmailIds.value.toList()
                var successCount = 0
                var errorCount = 0

                selectedIds.forEach { emailId ->
                    when (emailRepository.archiveEmail(emailId)) {
                        is Result.Success -> successCount++
                        is Result.Error -> errorCount++
                        else -> {}
                    }
                }

                if (errorCount > 0) {
                    _error.value = "Failed to archive $errorCount email(s)"
                }

                exitSelectionMode()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to archive emails"
            } finally {
                _isBulkOperationInProgress.value = false
            }
        }
    }

    fun bulkMoveToFolder(targetFolderId: Long) {
        viewModelScope.launch {
            _isBulkOperationInProgress.value = true
            _error.value = null

            try {
                val selectedIds = _selectedEmailIds.value.toList()
                var successCount = 0
                var errorCount = 0

                selectedIds.forEach { emailId ->
                    when (emailRepository.moveToFolder(emailId, targetFolderId)) {
                        is Result.Success -> successCount++
                        is Result.Error -> errorCount++
                        else -> {}
                    }
                }

                if (errorCount > 0) {
                    _error.value = "Failed to move $errorCount email(s)"
                }

                exitSelectionMode()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to move emails"
            } finally {
                _isBulkOperationInProgress.value = false
            }
        }
    }

    suspend fun getFoldersForAccount(accountId: Long): Result<List<Folder>> {
        return try {
            val folders = folderRepository.getFoldersByAccount(accountId).first()
            Result.Success(folders)
        } catch (e: Exception) {
            Result.Error(e, "Failed to load folders")
        }
    }

    // Swipe action methods
    fun performSwipeAction(emailId: String, action: SwipeAction) {
        if (action == SwipeAction.NONE) return

        viewModelScope.launch {
            val email = _emails.value.find { it.id == emailId } ?: return@launch

            // Store for undo
            pendingSwipeAction = PendingSwipeAction(
                emailId = emailId,
                action = action,
                originalReadState = if (action == SwipeAction.MARK_READ) email.isRead else null,
                originalFolderId = if (action == SwipeAction.ARCHIVE || action == SwipeAction.DELETE) folderId else null
            )

            when (action) {
                SwipeAction.ARCHIVE -> {
                    emailRepository.archiveEmail(emailId)
                    _swipeActionResult.value = "Email archived"
                }
                SwipeAction.DELETE -> {
                    emailRepository.deleteEmail(emailId)
                    _swipeActionResult.value = "Email deleted"
                }
                SwipeAction.MARK_READ -> {
                    val newReadState = !email.isRead
                    emailRepository.markAsRead(emailId, newReadState)
                    _swipeActionResult.value = if (newReadState) "Marked as read" else "Marked as unread"
                }
                SwipeAction.NONE -> {}
            }
        }
    }

    fun undoSwipeAction() {
        val pending = pendingSwipeAction ?: return
        viewModelScope.launch {
            when (pending.action) {
                SwipeAction.ARCHIVE, SwipeAction.DELETE -> {
                    // Move back to original folder
                    pending.originalFolderId?.let { originalFolderId ->
                        emailRepository.moveToFolder(pending.emailId, originalFolderId)
                    }
                }
                SwipeAction.MARK_READ -> {
                    pending.originalReadState?.let { originalState ->
                        emailRepository.markAsRead(pending.emailId, originalState)
                    }
                }
                SwipeAction.NONE -> {}
            }
            pendingSwipeAction = null
        }
    }

    fun finalizeSwipeAction() {
        pendingSwipeAction = null
    }

    fun clearSwipeActionResult() {
        _swipeActionResult.value = null
    }

    fun getSwipeLeftAction(): SwipeAction = appPreferences.getSwipeLeftAction()

    fun getSwipeRightAction(): SwipeAction = appPreferences.getSwipeRightAction()
}

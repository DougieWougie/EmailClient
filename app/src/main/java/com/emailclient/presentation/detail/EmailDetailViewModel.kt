package com.emailclient.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.data.local.AttachmentStorageManager
import com.emailclient.domain.model.Attachment
import com.emailclient.domain.model.DownloadState
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
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for email detail screen
 */
@HiltViewModel
class EmailDetailViewModel @Inject constructor(
    private val emailRepository: EmailRepository,
    private val folderRepository: FolderRepository,
    private val accountRepository: AccountRepository,
    private val attachmentStorageManager: AttachmentStorageManager
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

    private val _attachmentStates = MutableStateFlow<Map<String, AttachmentUiState>>(emptyMap())
    val attachmentStates: StateFlow<Map<String, AttachmentUiState>> = _attachmentStates.asStateFlow()

    private val _attachmentAction = MutableStateFlow<AttachmentAction?>(null)
    val attachmentAction: StateFlow<AttachmentAction?> = _attachmentAction.asStateFlow()

    private var pendingAction: PendingAction? = null

    data class PendingAction(
        val emailId: String,
        val originalFolderId: Long,
        val actionType: ActionType
    )

    enum class ActionType { DELETE, ARCHIVE }

    data class AttachmentUiState(
        val attachment: Attachment,
        val downloadState: DownloadState,
        val progress: Int = 0,
        val error: String? = null
    )

    sealed class AttachmentAction {
        data class Open(val file: File) : AttachmentAction()
        data class Error(val message: String) : AttachmentAction()
    }

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

                        // Initialize attachment states
                        initializeAttachmentStates(result.data)

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

    /**
     * Initialize attachment states for the loaded email
     */
    private suspend fun initializeAttachmentStates(email: Email) {
        val states = mutableMapOf<String, AttachmentUiState>()

        for (attachment in email.attachments) {
            // Check if attachment is already downloaded
            val file = attachmentStorageManager.getAttachmentFile(email.id, attachment.id)
            val downloadState = if (file?.exists() == true) {
                DownloadState.DOWNLOADED
            } else {
                DownloadState.NOT_DOWNLOADED
            }

            states[attachment.id] = AttachmentUiState(
                attachment = attachment,
                downloadState = downloadState
            )
        }

        _attachmentStates.value = states
    }

    /**
     * Download an attachment
     */
    fun downloadAttachment(attachmentId: String) {
        viewModelScope.launch {
            val email = _email.value ?: return@launch

            // Update state to downloading
            updateAttachmentState(attachmentId) {
                it.copy(downloadState = DownloadState.DOWNLOADING)
            }

            when (val result = emailRepository.downloadAttachment(email.id, attachmentId)) {
                is Result.Success -> {
                    updateAttachmentState(attachmentId) {
                        it.copy(
                            downloadState = DownloadState.DOWNLOADED,
                            error = null
                        )
                    }
                }
                is Result.Error -> {
                    updateAttachmentState(attachmentId) {
                        it.copy(
                            downloadState = DownloadState.DOWNLOAD_FAILED,
                            error = result.message ?: "Download failed"
                        )
                    }
                    _error.value = result.message ?: "Failed to download attachment"
                }
                else -> {
                    updateAttachmentState(attachmentId) {
                        it.copy(
                            downloadState = DownloadState.DOWNLOAD_FAILED,
                            error = "Unknown error"
                        )
                    }
                }
            }
        }
    }

    /**
     * Open an attachment
     */
    fun openAttachment(attachmentId: String) {
        viewModelScope.launch {
            val email = _email.value ?: return@launch
            val file = attachmentStorageManager.getAttachmentFile(email.id, attachmentId)

            if (file?.exists() == true) {
                _attachmentAction.value = AttachmentAction.Open(file)
            } else {
                // Download first if not already downloaded
                val state = _attachmentStates.value[attachmentId]
                if (state?.downloadState == DownloadState.NOT_DOWNLOADED ||
                    state?.downloadState == DownloadState.DOWNLOAD_FAILED) {
                    downloadAttachment(attachmentId)
                }
            }
        }
    }

    /**
     * Clear attachment action after it's been handled
     */
    fun clearAttachmentAction() {
        _attachmentAction.value = null
    }

    /**
     * Update the state of a specific attachment
     */
    private fun updateAttachmentState(
        attachmentId: String,
        update: (AttachmentUiState) -> AttachmentUiState
    ) {
        _attachmentStates.value = _attachmentStates.value.toMutableMap().apply {
            this[attachmentId]?.let { this[attachmentId] = update(it) }
        }
    }
}

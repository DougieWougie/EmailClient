package com.emailclient.presentation.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.model.Folder
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.FolderRepository
import com.emailclient.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for folder management screen
 */
@HiltViewModel
class FolderManagementViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _actionResult = MutableStateFlow<String?>(null)
    val actionResult: StateFlow<String?> = _actionResult.asStateFlow()

    init {
        loadFolders()
    }

    /**
     * Load folders for the default account
     */
    fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Get default account
                when (val result = accountRepository.getDefaultAccount()) {
                    is Result.Success -> {
                        val account = result.data

                        // Observe folders for this account
                        folderRepository.getFoldersByAccount(account.id)
                            .collect { folderList ->
                                _folders.value = folderList
                                _isLoading.value = false
                            }
                    }
                    is Result.Error -> {
                        _error.value = result.message ?: "Failed to load account"
                        _isLoading.value = false
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load folders"
                _isLoading.value = false
            }
        }
    }

    /**
     * Create a new folder
     */
    fun createFolder(folderName: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                when (val accountResult = accountRepository.getDefaultAccount()) {
                    is Result.Success -> {
                        val account = accountResult.data

                        when (val result = folderRepository.createFolder(account.id, folderName)) {
                            is Result.Success -> {
                                _actionResult.value = "Folder '$folderName' created successfully"
                            }
                            is Result.Error -> {
                                _error.value = result.message ?: "Failed to create folder"
                            }
                            else -> {
                                _error.value = "Unknown error occurred"
                            }
                        }
                    }
                    is Result.Error -> {
                        _error.value = accountResult.message ?: "Failed to load account"
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create folder"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Rename an existing folder
     */
    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                when (val result = folderRepository.renameFolder(folderId, newName)) {
                    is Result.Success -> {
                        _actionResult.value = "Folder renamed to '$newName'"
                    }
                    is Result.Error -> {
                        _error.value = result.message ?: "Failed to rename folder"
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to rename folder"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Delete a folder
     */
    fun deleteFolder(folderId: Long, folderName: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                when (val result = folderRepository.deleteFolder(folderId)) {
                    is Result.Success -> {
                        _actionResult.value = "Folder '$folderName' deleted"
                    }
                    is Result.Error -> {
                        _error.value = result.message ?: "Failed to delete folder"
                    }
                    else -> {
                        _error.value = "Unknown error occurred"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete folder"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearActionResult() {
        _actionResult.value = null
    }

    fun clearError() {
        _error.value = null
    }
}

package com.emailclient.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.model.Email
import com.emailclient.domain.repository.EmailRepository
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
    private val emailRepository: EmailRepository
) : ViewModel() {

    private val _email = MutableStateFlow<Email?>(null)
    val email: StateFlow<Email?> = _email.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _actionResult = MutableStateFlow<String?>(null)
    val actionResult: StateFlow<String?> = _actionResult.asStateFlow()

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
            when (val result = emailRepository.deleteEmail(emailId)) {
                is Result.Success -> {
                    _actionResult.value = "Email deleted"
                }
                is Result.Error -> {
                    _error.value = result.message ?: "Failed to delete email"
                }
                else -> {
                    _error.value = "Unknown error occurred"
                }
            }
        }
    }

    /**
     * Archive email by ID
     */
    fun archiveEmail(emailId: String) {
        viewModelScope.launch {
            when (val result = emailRepository.archiveEmail(emailId)) {
                is Result.Success -> {
                    _actionResult.value = "Email archived"
                }
                is Result.Error -> {
                    _error.value = result.message ?: "Failed to archive email"
                }
                else -> {
                    _error.value = "Unknown error occurred"
                }
            }
        }
    }

    fun clearActionResult() {
        _actionResult.value = null
    }
}

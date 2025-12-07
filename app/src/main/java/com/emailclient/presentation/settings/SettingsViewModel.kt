package com.emailclient.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.model.Account
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.util.Result
import com.emailclient.util.WorkManagerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for settings screen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {

    /**
     * List of all accounts
     */
    val accounts: StateFlow<List<Account>> = accountRepository.getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Delete an account
     */
    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading

            when (val result = accountRepository.deleteAccount(accountId)) {
                is Result.Success -> {
                    _uiState.value = SettingsUiState.AccountDeleted
                }
                is Result.Error -> {
                    _uiState.value = SettingsUiState.Error(
                        result.message ?: "Failed to delete account"
                    )
                }
                else -> {
                    _uiState.value = SettingsUiState.Error("Unknown error occurred")
                }
            }
        }
    }

    /**
     * Set default account
     */
    fun setDefaultAccount(accountId: Long) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading

            when (val result = accountRepository.setDefaultAccount(accountId)) {
                is Result.Success -> {
                    _uiState.value = SettingsUiState.DefaultAccountSet
                }
                is Result.Error -> {
                    _uiState.value = SettingsUiState.Error(
                        result.message ?: "Failed to set default account"
                    )
                }
                else -> {
                    _uiState.value = SettingsUiState.Error("Unknown error occurred")
                }
            }
        }
    }

    /**
     * Toggle sync for an account
     */
    fun toggleAccountSync(accountId: Long, enabled: Boolean) {
        viewModelScope.launch {
            when (val result = accountRepository.setSyncEnabled(accountId, enabled)) {
                is Result.Success -> {
                    // Success - state will update via Flow
                    if (enabled) {
                        // Re-schedule sync if enabling
                        workManagerHelper.schedulePeriodicSync()
                    }
                }
                is Result.Error -> {
                    _uiState.value = SettingsUiState.Error(
                        result.message ?: "Failed to update sync settings"
                    )
                }
                else -> {
                    _uiState.value = SettingsUiState.Error("Unknown error occurred")
                }
            }
        }
    }

    /**
     * Trigger manual sync for all accounts
     */
    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Syncing
            workManagerHelper.syncNow()
            _uiState.value = SettingsUiState.SyncStarted
        }
    }

    fun resetState() {
        _uiState.value = SettingsUiState.Idle
    }
}

/**
 * UI state for settings screen
 */
sealed class SettingsUiState {
    object Idle : SettingsUiState()
    object Loading : SettingsUiState()
    object AccountDeleted : SettingsUiState()
    object DefaultAccountSet : SettingsUiState()
    object Syncing : SettingsUiState()
    object SyncStarted : SettingsUiState()
    data class Error(val message: String) : SettingsUiState()
}

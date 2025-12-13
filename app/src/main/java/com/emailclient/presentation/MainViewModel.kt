package com.emailclient.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.domain.model.Account
import com.emailclient.domain.model.Folder
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.FolderRepository
import com.emailclient.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MainActivity
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    /**
     * True if there is at least one account
     */
    val hasAccounts: StateFlow<Boolean> = accountRepository.getAllAccounts()
        .map { accounts -> accounts.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Start with true to prevent flicker
        )

    /**
     * Current account state
     */
    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    /**
     * All accounts for account switcher
     */
    val allAccounts: StateFlow<List<Account>> = accountRepository.getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Folders for current account
     */
    val folders: StateFlow<List<Folder>> = currentAccount
        .filterNotNull()
        .flatMapLatest { account ->
            folderRepository.getFoldersByAccount(account.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadDefaultAccount()
    }

    /**
     * Load the default account or first available account
     */
    private fun loadDefaultAccount() {
        viewModelScope.launch {
            when (val result = accountRepository.getDefaultAccount()) {
                is Result.Success -> _currentAccount.value = result.data
                else -> {
                    // Fall back to first account if no default is set
                    allAccounts.value.firstOrNull()?.let {
                        _currentAccount.value = it
                    }
                }
            }
        }
    }

    /**
     * Switch to a different account
     */
    fun switchAccount(accountId: Long) {
        viewModelScope.launch {
            when (val result = accountRepository.getAccountById(accountId)) {
                is Result.Success -> {
                    _currentAccount.value = result.data
                    // Optionally set as default account
                    accountRepository.setDefaultAccount(accountId)
                }
                is Result.Error -> {
                    // Handle error - could emit to error state if needed
                }
                is Result.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }
}

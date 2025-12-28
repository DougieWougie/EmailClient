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
import kotlinx.coroutines.flow.first
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
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
        ensureAccountsHaveFolders()
    }

    /**
     * Load the default account or first available account
     */
    private fun loadDefaultAccount() {
        viewModelScope.launch {
            android.util.Log.d("MainViewModel", "Loading default account...")
            when (val result = accountRepository.getDefaultAccount()) {
                is Result.Success -> {
                    android.util.Log.d("MainViewModel", "Default account loaded: ${result.data?.email}")
                    _currentAccount.value = result.data
                }
                else -> {
                    android.util.Log.d("MainViewModel", "No default account, checking all accounts...")
                    // Wait for accounts to load from repository
                    val accounts = accountRepository.getAllAccounts().first()
                    android.util.Log.d("MainViewModel", "Found ${accounts.size} accounts")
                    accounts.firstOrNull()?.let {
                        android.util.Log.d("MainViewModel", "Using first account: ${it.email}")
                        _currentAccount.value = it
                        // Set it as default for future
                        accountRepository.setDefaultAccount(it.id)
                    } ?: android.util.Log.d("MainViewModel", "No accounts found")
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

    /**
     * Ensure all accounts have folders (creates defaults if missing)
     */
    private fun ensureAccountsHaveFolders() {
        viewModelScope.launch {
            val accounts = accountRepository.getAllAccounts().first()
            accounts.forEach { account ->
                val folderList = folderRepository.getFoldersByAccount(account.id).first()
                if (folderList.isEmpty()) {
                    android.util.Log.w("MainViewModel", "Account ${account.email} has no folders, creating defaults")
                    createDefaultFoldersForAccount(account.id)
                }
            }
        }
    }

    /**
     * Create default folders for an account
     */
    private suspend fun createDefaultFoldersForAccount(accountId: Long) {
        accountRepository.ensureFoldersExist(accountId)
    }
}

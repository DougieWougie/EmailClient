package com.emailclient.presentation.settings

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.emailclient.databinding.FragmentSettingsBinding
import com.emailclient.presentation.setup.AccountSetupActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for app settings
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var accountAdapter: AccountAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSyncIntervalDropdown()
        setupAnimationToggle()
        setupButtons()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        accountAdapter = AccountAdapter(
            onAccountClick = { account ->
                // Could navigate to account detail/edit screen
                showAccountOptions(account.id, account.email)
            },
            onSetDefault = { accountId ->
                viewModel.setDefaultAccount(accountId)
            },
            onToggleSync = { accountId, enabled ->
                viewModel.toggleAccountSync(accountId, enabled)
            },
            onDeleteAccount = { accountId, email ->
                showDeleteConfirmation(accountId, email)
            }
        )

        binding.recyclerViewAccounts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = accountAdapter
        }
    }

    private fun setupSyncIntervalDropdown() {
        val options = viewModel.getSyncIntervalOptions()
        val labels = options.map { it.label }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels
        )
        binding.syncIntervalDropdown.setAdapter(adapter)

        // Set current value
        val currentInterval = viewModel.getSyncInterval()
        val currentOption = options.find { it.minutes == currentInterval }
        currentOption?.let {
            binding.syncIntervalDropdown.setText(it.label, false)
        }

        // Handle selection
        binding.syncIntervalDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = options[position]
            viewModel.setSyncInterval(selectedOption.minutes)
            Snackbar.make(
                binding.root,
                "Sync frequency updated to ${selectedOption.label}",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupAnimationToggle() {
        // Set initial state from preferences
        binding.switchAnimations.isChecked = viewModel.areAnimationsEnabled()

        // Handle toggle changes
        binding.switchAnimations.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAnimationsEnabled(isChecked)
            Snackbar.make(
                binding.root,
                if (isChecked) "Activity animations enabled" else "Activity animations disabled",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupButtons() {
        binding.buttonAddAccount.setOnClickListener {
            // Navigate to account setup
            val intent = Intent(requireContext(), AccountSetupActivity::class.java)
            if (viewModel.areAnimationsEnabled()) {
                val options = ActivityOptions.makeSceneTransitionAnimation(requireActivity())
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
            }
        }

        binding.buttonManageFolders.setOnClickListener {
            // Navigate to folder management
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigate(com.emailclient.R.id.action_settings_to_folders)
        }

        binding.buttonSyncNow.setOnClickListener {
            viewModel.syncNow()
        }
    }

    private fun showAccountOptions(accountId: Long, email: String) {
        val options = arrayOf(
            "Set as Default",
            "Delete Account"
        )

        AlertDialog.Builder(requireContext())
            .setTitle(email)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.setDefaultAccount(accountId)
                    1 -> showDeleteConfirmation(accountId, email)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(accountId: Long, email: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete $email? This will remove all emails and folders for this account.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAccount(accountId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.accounts.collect { accounts ->
                        accountAdapter.submitList(accounts)
                        binding.textEmptyState.visibility =
                            if (accounts.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is SettingsUiState.Idle -> {
                                binding.progressBar.visibility = View.GONE
                                binding.buttonSyncNow.isEnabled = true
                            }

                            is SettingsUiState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                            }

                            is SettingsUiState.AccountDeleted -> {
                                binding.progressBar.visibility = View.GONE
                                Snackbar.make(
                                    binding.root,
                                    "Account deleted",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                viewModel.resetState()
                            }

                            is SettingsUiState.DefaultAccountSet -> {
                                binding.progressBar.visibility = View.GONE
                                Snackbar.make(
                                    binding.root,
                                    "Default account updated",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                viewModel.resetState()
                            }

                            is SettingsUiState.Syncing -> {
                                binding.buttonSyncNow.isEnabled = false
                            }

                            is SettingsUiState.SyncStarted -> {
                                binding.buttonSyncNow.isEnabled = true
                                Snackbar.make(
                                    binding.root,
                                    "Sync started",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                viewModel.resetState()
                            }

                            is SettingsUiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.buttonSyncNow.isEnabled = true
                                Snackbar.make(
                                    binding.root,
                                    state.message,
                                    Snackbar.LENGTH_LONG
                                ).show()
                                viewModel.resetState()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

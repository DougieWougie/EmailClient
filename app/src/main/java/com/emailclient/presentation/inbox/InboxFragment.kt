package com.emailclient.presentation.inbox

import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.emailclient.R
import com.emailclient.databinding.FragmentInboxBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment displaying the inbox email list
 */
@AndroidEntryPoint
class InboxFragment : Fragment() {

    private var _binding: FragmentInboxBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InboxViewModel by viewModels()
    private lateinit var emailAdapter: EmailAdapter
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle back press to exit selection mode
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (viewModel.isSelectionMode.value) {
                viewModel.exitSelectionMode()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        emailAdapter = EmailAdapter(
            onEmailClick = { email ->
                val action = InboxFragmentDirections.actionInboxToDetail(email.id)
                findNavController().navigate(action)
            },
            onEmailLongClick = { email ->
                // Enter selection mode on long press
                viewModel.enterSelectionMode(email.id)
            },
            isSelectionMode = { viewModel.isSelectionMode.value },
            isEmailSelected = { emailId -> viewModel.isEmailSelected(emailId) },
            onSelectionToggle = { email ->
                viewModel.toggleEmailSelection(email.id)
            }
        )

        binding.recyclerViewEmails.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = emailAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshEmails()
        }
    }

    private fun setupFab() {
        binding.fabCompose.setOnClickListener {
            val action = InboxFragmentDirections.actionInboxToCompose(null, false, false)
            findNavController().navigate(action)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.emails.collect { emails ->
                        emailAdapter.submitList(emails)
                        binding.emptyStateText.visibility =
                            if (emails.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.swipeRefreshLayout.isRefreshing = isLoading
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }

                launch {
                    viewModel.isSelectionMode.collect { isSelectionMode ->
                        if (isSelectionMode) {
                            startActionMode()
                        } else {
                            actionMode?.finish()
                        }
                        emailAdapter.notifySelectionChanged()
                    }
                }

                launch {
                    viewModel.selectedEmailIds.collect { selectedIds ->
                        actionMode?.title = "${selectedIds.size} selected"
                        emailAdapter.notifySelectionChanged()
                    }
                }

                launch {
                    viewModel.isBulkOperationInProgress.collect { inProgress ->
                        // Disable swipe refresh during bulk operations
                        binding.swipeRefreshLayout.isEnabled = !inProgress

                        // Show loading indicator in ActionMode
                        actionMode?.invalidate()
                    }
                }
            }
        }
    }

    private fun startActionMode() {
        if (actionMode != null) return

        actionMode = requireActivity().startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.menu_email_selection, menu)
                // Hide FAB during selection mode
                binding.fabCompose.hide()
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                // Update title with selection count
                val count = viewModel.getSelectedCount()
                mode.title = "$count selected"

                // Show/hide menu items based on state
                val isOperationInProgress = viewModel.isBulkOperationInProgress.value
                menu.findItem(R.id.action_select_all)?.isEnabled = !isOperationInProgress
                menu.findItem(R.id.action_mark_read)?.isEnabled = !isOperationInProgress
                menu.findItem(R.id.action_mark_unread)?.isEnabled = !isOperationInProgress
                menu.findItem(R.id.action_delete)?.isEnabled = !isOperationInProgress
                menu.findItem(R.id.action_archive)?.isEnabled = !isOperationInProgress
                menu.findItem(R.id.action_move_to_folder)?.isEnabled = !isOperationInProgress

                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_select_all -> {
                        viewModel.selectAllEmails()
                        true
                    }
                    R.id.action_mark_read -> {
                        viewModel.bulkMarkAsRead(true)
                        true
                    }
                    R.id.action_mark_unread -> {
                        viewModel.bulkMarkAsRead(false)
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog()
                        true
                    }
                    R.id.action_archive -> {
                        viewModel.bulkArchive()
                        true
                    }
                    R.id.action_move_to_folder -> {
                        showFolderSelectionDialog()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                viewModel.exitSelectionMode()
                // Show FAB again
                binding.fabCompose.show()
            }
        })
    }

    private fun showDeleteConfirmationDialog() {
        val count = viewModel.getSelectedCount()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete emails")
            .setMessage("Delete $count email(s)? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.bulkDelete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFolderSelectionDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get current account ID from ViewModel
                val accountId = viewModel.getCurrentAccountId()
                if (accountId == null) {
                    Snackbar.make(binding.root, "No account selected", Snackbar.LENGTH_SHORT).show()
                    return@launch
                }

                // Get folders for the account
                val foldersResult = viewModel.getFoldersForAccount(accountId)
                when (foldersResult) {
                    is com.emailclient.util.Result.Success -> {
                        val folders = foldersResult.data
                        val folderNames = folders.map { it.name }.toTypedArray()

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Move to folder")
                            .setItems(folderNames) { _, which ->
                                val selectedFolder = folders[which]
                                viewModel.bulkMoveToFolder(selectedFolder.id)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    is com.emailclient.util.Result.Error -> {
                        Snackbar.make(
                            binding.root,
                            "Failed to load folders",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Error loading folders",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

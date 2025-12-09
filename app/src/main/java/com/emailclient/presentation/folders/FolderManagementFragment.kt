package com.emailclient.presentation.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.emailclient.databinding.FragmentFolderManagementBinding
import com.emailclient.domain.model.Folder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for managing email folders
 */
@AndroidEntryPoint
class FolderManagementFragment : Fragment() {

    private var _binding: FragmentFolderManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FolderManagementViewModel by viewModels()
    private lateinit var folderAdapter: FolderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onRenameClick = { folder -> showRenameDialog(folder) },
            onDeleteClick = { folder -> showDeleteConfirmation(folder) }
        )

        binding.recyclerViewFolders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = folderAdapter
        }
    }

    private fun setupFab() {
        binding.fabCreateFolder.setOnClickListener {
            showCreateFolderDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.folders.collect { folders ->
                        folderAdapter.submitList(folders)
                        binding.emptyStateText.visibility =
                            if (folders.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility =
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }

                launch {
                    viewModel.actionResult.collect { result ->
                        result?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                            viewModel.clearActionResult()
                        }
                    }
                }
            }
        }
    }

    private fun showCreateFolderDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Folder name"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create New Folder")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val folderName = editText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    viewModel.createFolder(folderName)
                } else {
                    Snackbar.make(binding.root, "Please enter a folder name", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(folder: Folder) {
        val editText = EditText(requireContext()).apply {
            hint = "New folder name"
            setText(folder.displayName)
            setPadding(48, 32, 48, 32)
            selectAll()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Rename Folder")
            .setMessage("Rename '${folder.displayName}'")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != folder.displayName) {
                    viewModel.renameFolder(folder.id, newName)
                } else if (newName.isEmpty()) {
                    Snackbar.make(binding.root, "Please enter a folder name", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(folder: Folder) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Folder")
            .setMessage("Are you sure you want to delete '${folder.displayName}'?\n\nThis folder must be empty to be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteFolder(folder.id, folder.displayName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.emailclient.presentation.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.emailclient.R
import com.emailclient.databinding.ItemFolderBinding
import com.emailclient.domain.model.Folder
import com.emailclient.domain.model.FolderType

/**
 * Adapter for displaying folders in folder management screen
 */
class FolderAdapter(
    private val onRenameClick: (Folder) -> Unit,
    private val onDeleteClick: (Folder) -> Unit
) : ListAdapter<Folder, FolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding, onRenameClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FolderViewHolder(
        private val binding: ItemFolderBinding,
        private val onRenameClick: (Folder) -> Unit,
        private val onDeleteClick: (Folder) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: Folder) {
            binding.textFolderName.text = folder.displayName
            binding.textFolderType.text = folder.type.name
            binding.textCount.text = "${folder.totalCount}"

            // Set folder icon based on type
            val iconRes = when (folder.type) {
                FolderType.INBOX -> R.drawable.ic_inbox
                FolderType.SENT -> R.drawable.ic_send
                FolderType.DRAFTS -> R.drawable.ic_drafts
                FolderType.TRASH -> R.drawable.ic_delete
                FolderType.SPAM -> R.drawable.ic_spam
                FolderType.ARCHIVE -> R.drawable.ic_archive
                FolderType.CUSTOM -> R.drawable.ic_folder
            }
            binding.iconFolder.setImageResource(iconRes)

            // Disable rename/delete for system folders
            val isCustomFolder = folder.type == FolderType.CUSTOM

            binding.btnRename.visibility = if (isCustomFolder) View.VISIBLE else View.GONE
            binding.btnDelete.visibility = if (isCustomFolder) View.VISIBLE else View.GONE

            binding.btnRename.setOnClickListener {
                onRenameClick(folder)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(folder)
            }
        }
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<Folder>() {
        override fun areItemsTheSame(oldItem: Folder, newItem: Folder): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Folder, newItem: Folder): Boolean {
            return oldItem == newItem
        }
    }
}

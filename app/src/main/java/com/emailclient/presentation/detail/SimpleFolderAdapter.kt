package com.emailclient.presentation.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.emailclient.R
import com.emailclient.databinding.ItemFolderSimpleBinding
import com.emailclient.domain.model.Folder
import com.emailclient.domain.model.FolderType

/**
 * Simple adapter for folder selection dialog
 */
class SimpleFolderAdapter(
    private val onFolderClick: (Folder) -> Unit
) : ListAdapter<Folder, SimpleFolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderSimpleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding, onFolderClick)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FolderViewHolder(
        private val binding: ItemFolderSimpleBinding,
        private val onFolderClick: (Folder) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: Folder) {
            binding.textFolderName.text = folder.displayName
            binding.textCount.text = "(${folder.totalCount})"

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

            binding.root.setOnClickListener {
                onFolderClick(folder)
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

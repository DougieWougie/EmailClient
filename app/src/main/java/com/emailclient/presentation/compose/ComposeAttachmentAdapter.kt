package com.emailclient.presentation.compose

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.emailclient.databinding.ItemComposeAttachmentBinding

class ComposeAttachmentAdapter(
    private val onRemoveClick: (String) -> Unit
) : ListAdapter<AttachmentItem, ComposeAttachmentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemComposeAttachmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onRemoveClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemComposeAttachmentBinding,
        private val onRemoveClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AttachmentItem) {
            binding.textFileName.text = item.fileName
            binding.textFileSize.text = formatFileSize(item.size)

            binding.buttonRemove.setOnClickListener {
                onRemoveClick(item.id)
            }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AttachmentItem>() {
        override fun areItemsTheSame(old: AttachmentItem, new: AttachmentItem) =
            old.id == new.id

        override fun areContentsTheSame(old: AttachmentItem, new: AttachmentItem) =
            old == new
    }
}

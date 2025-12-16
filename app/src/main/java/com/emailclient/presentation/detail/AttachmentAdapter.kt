package com.emailclient.presentation.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.emailclient.R
import com.emailclient.databinding.ItemAttachmentBinding
import com.emailclient.domain.model.DownloadState

class AttachmentAdapter(
    private val onDownloadClick: (String) -> Unit,
    private val onOpenClick: (String) -> Unit
) : ListAdapter<EmailDetailViewModel.AttachmentUiState, AttachmentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAttachmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onDownloadClick, onOpenClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAttachmentBinding,
        private val onDownloadClick: (String) -> Unit,
        private val onOpenClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: EmailDetailViewModel.AttachmentUiState) {
            binding.textFileName.text = state.attachment.fileName
            binding.textFileSize.text = formatFileSize(state.attachment.size)

            when (state.downloadState) {
                DownloadState.NOT_DOWNLOADED -> {
                    binding.buttonDownload.visibility = View.VISIBLE
                    binding.progressDownload.visibility = View.GONE
                    binding.buttonDownload.setIconResource(R.drawable.ic_download)
                    binding.buttonDownload.setOnClickListener {
                        onDownloadClick(state.attachment.id)
                    }
                }
                DownloadState.DOWNLOADING -> {
                    binding.buttonDownload.visibility = View.GONE
                    binding.progressDownload.visibility = View.VISIBLE
                }
                DownloadState.DOWNLOADED -> {
                    binding.buttonDownload.visibility = View.VISIBLE
                    binding.progressDownload.visibility = View.GONE
                    binding.buttonDownload.setIconResource(R.drawable.ic_open)
                    binding.buttonDownload.setOnClickListener {
                        onOpenClick(state.attachment.id)
                    }
                }
                DownloadState.DOWNLOAD_FAILED -> {
                    binding.buttonDownload.visibility = View.VISIBLE
                    binding.progressDownload.visibility = View.GONE
                    binding.buttonDownload.setIconResource(R.drawable.ic_retry)
                    binding.buttonDownload.setOnClickListener {
                        onDownloadClick(state.attachment.id)
                    }
                }
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

    class DiffCallback : DiffUtil.ItemCallback<EmailDetailViewModel.AttachmentUiState>() {
        override fun areItemsTheSame(
            old: EmailDetailViewModel.AttachmentUiState,
            new: EmailDetailViewModel.AttachmentUiState
        ) = old.attachment.id == new.attachment.id

        override fun areContentsTheSame(
            old: EmailDetailViewModel.AttachmentUiState,
            new: EmailDetailViewModel.AttachmentUiState
        ) = old == new
    }
}

package com.emailclient.presentation.inbox

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.emailclient.databinding.ItemEmailBinding
import com.emailclient.domain.model.Email

/**
 * RecyclerView adapter for displaying emails in a list
 */
class EmailAdapter(
    private val onEmailClick: (Email) -> Unit,
    private val onEmailLongClick: (Email) -> Unit
) : ListAdapter<Email, EmailAdapter.EmailViewHolder>(EmailDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmailViewHolder {
        val binding = ItemEmailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EmailViewHolder(binding, onEmailClick, onEmailLongClick)
    }

    override fun onBindViewHolder(holder: EmailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EmailViewHolder(
        private val binding: ItemEmailBinding,
        private val onEmailClick: (Email) -> Unit,
        private val onEmailLongClick: (Email) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(email: Email) {
            binding.apply {
                textFrom.text = email.from.displayName
                textSubject.text = email.subject
                textSnippet.text = email.snippet.ifEmpty { email.body.take(100) }
                textDate.text = DateUtils.getRelativeTimeSpanString(
                    email.receivedDate.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )

                // Visual indicators
                root.alpha = if (email.isRead) 0.6f else 1.0f
                textSubject.setTypeface(
                    null,
                    if (email.isRead) android.graphics.Typeface.NORMAL
                    else android.graphics.Typeface.BOLD
                )

                iconAttachment.visibility =
                    if (email.hasAttachments) android.view.View.VISIBLE
                    else android.view.View.GONE

                iconFlagged.visibility =
                    if (email.isFlagged) android.view.View.VISIBLE
                    else android.view.View.GONE

                root.setOnClickListener { onEmailClick(email) }
                root.setOnLongClickListener {
                    onEmailLongClick(email)
                    true
                }
            }
        }
    }

    private class EmailDiffCallback : DiffUtil.ItemCallback<Email>() {
        override fun areItemsTheSame(oldItem: Email, newItem: Email): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Email, newItem: Email): Boolean {
            return oldItem == newItem
        }
    }
}

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
    private val onEmailLongClick: (Email) -> Unit,
    private val isSelectionMode: () -> Boolean,
    private val isEmailSelected: (String) -> Boolean,
    private val onSelectionToggle: (Email) -> Unit
) : ListAdapter<Email, EmailAdapter.EmailViewHolder>(EmailDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmailViewHolder {
        val binding = ItemEmailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EmailViewHolder(
            binding,
            onEmailClick,
            onEmailLongClick,
            isSelectionMode,
            isEmailSelected,
            onSelectionToggle
        )
    }

    override fun onBindViewHolder(holder: EmailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: EmailViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Partial update for selection state only
            holder.updateSelectionState(getItem(position))
        }
    }

    fun notifySelectionChanged() {
        notifyItemRangeChanged(0, itemCount, "SELECTION_CHANGED")
    }

    class EmailViewHolder(
        private val binding: ItemEmailBinding,
        private val onEmailClick: (Email) -> Unit,
        private val onEmailLongClick: (Email) -> Unit,
        private val isSelectionMode: () -> Boolean,
        private val isEmailSelected: (String) -> Boolean,
        private val onSelectionToggle: (Email) -> Unit
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

                // Selection state
                updateSelectionState(email)

                // Click handlers
                root.setOnClickListener {
                    if (isSelectionMode()) {
                        onSelectionToggle(email)
                    } else {
                        onEmailClick(email)
                    }
                }

                root.setOnLongClickListener {
                    if (!isSelectionMode()) {
                        onEmailLongClick(email)
                    }
                    true
                }
            }
        }

        fun updateSelectionState(email: Email) {
            binding.apply {
                val inSelectionMode = isSelectionMode()
                val selected = isEmailSelected(email.id)

                // Show/hide checkbox
                checkboxSelect.visibility =
                    if (inSelectionMode) android.view.View.VISIBLE
                    else android.view.View.GONE

                checkboxSelect.isChecked = selected

                // Visual feedback for selection
                if (inSelectionMode && selected) {
                    root.strokeWidth = 4
                    root.strokeColor = root.context.getColor(
                        com.emailclient.R.color.md_theme_light_primary
                    )
                } else {
                    root.strokeWidth = 0
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

        override fun getChangePayload(oldItem: Email, newItem: Email): Any? {
            // Return payload for selection state changes only
            return if (oldItem.id == newItem.id) {
                "SELECTION_CHANGED"
            } else {
                null
            }
        }
    }
}

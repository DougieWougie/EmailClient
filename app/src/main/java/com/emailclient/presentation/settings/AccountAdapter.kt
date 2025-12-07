package com.emailclient.presentation.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.emailclient.databinding.ItemAccountBinding
import com.emailclient.domain.model.Account

/**
 * Adapter for account list in settings
 */
class AccountAdapter(
    private val onAccountClick: (Account) -> Unit,
    private val onSetDefault: (Long) -> Unit,
    private val onToggleSync: (Long, Boolean) -> Unit,
    private val onDeleteAccount: (Long, String) -> Unit
) : ListAdapter<Account, AccountAdapter.AccountViewHolder>(AccountDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AccountViewHolder(
        private val binding: ItemAccountBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(account: Account) {
            binding.textEmail.text = account.email
            binding.textDisplayName.text = account.displayName

            // Show server info
            binding.textServerInfo.text = buildString {
                append("IMAP: ${account.imapConfig.host}:${account.imapConfig.port}")
                append("\n")
                append("SMTP: ${account.smtpConfig.host}:${account.smtpConfig.port}")
            }

            // Default badge
            binding.chipDefault.visibility = if (account.isDefault) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            // Sync switch
            binding.switchSync.isChecked = account.syncEnabled
            binding.switchSync.setOnCheckedChangeListener { _, isChecked ->
                onToggleSync(account.id, isChecked)
            }

            // Click listeners
            binding.root.setOnClickListener {
                onAccountClick(account)
            }

            binding.buttonSetDefault.setOnClickListener {
                onSetDefault(account.id)
            }

            binding.buttonDelete.setOnClickListener {
                onDeleteAccount(account.id, account.email)
            }

            // Disable set default button if already default
            binding.buttonSetDefault.isEnabled = !account.isDefault
        }
    }

    private class AccountDiffCallback : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem == newItem
        }
    }
}

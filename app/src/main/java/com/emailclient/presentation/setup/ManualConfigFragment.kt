package com.emailclient.presentation.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.emailclient.R
import com.emailclient.databinding.FragmentManualConfigBinding
import com.emailclient.domain.model.SecurityType
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for manual IMAP/SMTP configuration
 */
@AndroidEntryPoint
class ManualConfigFragment : Fragment() {

    private var _binding: FragmentManualConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccountSetupViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        setupButtons()
        observeViewModel()
    }

    private fun setupSpinners() {
        val securityOptions = arrayOf("None", "SSL/TLS", "STARTTLS")

        binding.spinnerImapSecurity.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            securityOptions
        )
        binding.spinnerImapSecurity.setSelection(1) // Default to SSL/TLS

        binding.spinnerSmtpSecurity.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            securityOptions
        )
        binding.spinnerSmtpSecurity.setSelection(1) // Default to SSL/TLS
    }

    private fun setupButtons() {
        binding.buttonTestConnection.setOnClickListener {
            if (validateInputs()) {
                testConnection()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (binding.editTextEmail.text.isNullOrBlank()) {
            binding.textInputEmail.error = "Email is required"
            isValid = false
        } else {
            binding.textInputEmail.error = null
        }

        if (binding.editTextPassword.text.isNullOrBlank()) {
            binding.textInputPassword.error = "Password is required"
            isValid = false
        } else {
            binding.textInputPassword.error = null
        }

        if (binding.editTextDisplayName.text.isNullOrBlank()) {
            binding.textInputDisplayName.error = "Display name is required"
            isValid = false
        } else {
            binding.textInputDisplayName.error = null
        }

        if (binding.editTextImapHost.text.isNullOrBlank()) {
            binding.textInputImapHost.error = "IMAP host is required"
            isValid = false
        } else {
            binding.textInputImapHost.error = null
        }

        if (binding.editTextImapPort.text.isNullOrBlank()) {
            binding.textInputImapPort.error = "IMAP port is required"
            isValid = false
        } else {
            binding.textInputImapPort.error = null
        }

        if (binding.editTextSmtpHost.text.isNullOrBlank()) {
            binding.textInputSmtpHost.error = "SMTP host is required"
            isValid = false
        } else {
            binding.textInputSmtpHost.error = null
        }

        if (binding.editTextSmtpPort.text.isNullOrBlank()) {
            binding.textInputSmtpPort.error = "SMTP port is required"
            isValid = false
        } else {
            binding.textInputSmtpPort.error = null
        }

        return isValid
    }

    private fun testConnection() {
        val email = binding.editTextEmail.text.toString().trim()
        val password = binding.editTextPassword.text.toString()
        val displayName = binding.editTextDisplayName.text.toString().trim()

        val imapHost = binding.editTextImapHost.text.toString().trim()
        val imapPort = binding.editTextImapPort.text.toString().toIntOrNull() ?: 993
        val imapSecurity = getSecurityType(binding.spinnerImapSecurity.selectedItemPosition)

        val smtpHost = binding.editTextSmtpHost.text.toString().trim()
        val smtpPort = binding.editTextSmtpPort.text.toString().toIntOrNull() ?: 465
        val smtpSecurity = getSecurityType(binding.spinnerSmtpSecurity.selectedItemPosition)

        viewModel.testConnection(
            email, password, displayName,
            imapHost, imapPort, imapSecurity,
            smtpHost, smtpPort, smtpSecurity
        )
    }

    private fun getSecurityType(position: Int): SecurityType {
        return when (position) {
            0 -> SecurityType.NONE
            1 -> SecurityType.SSL_TLS
            2 -> SecurityType.STARTTLS
            else -> SecurityType.SSL_TLS
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AccountSetupState.Idle -> {
                            binding.progressBar.visibility = View.GONE
                            binding.buttonTestConnection.isEnabled = true
                        }

                        is AccountSetupState.Testing -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.buttonTestConnection.isEnabled = false
                        }

                        is AccountSetupState.TestSuccess -> {
                            binding.progressBar.visibility = View.GONE
                            binding.buttonTestConnection.isEnabled = true

                            Snackbar.make(
                                binding.root,
                                "Connection successful! Adding account...",
                                Snackbar.LENGTH_SHORT
                            ).show()

                            // Add the account
                            viewModel.addAccount(state.account, state.password)
                        }

                        is AccountSetupState.Adding -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.buttonTestConnection.isEnabled = false
                        }

                        is AccountSetupState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            (requireActivity() as AccountSetupActivity).finishSetup()
                        }

                        is AccountSetupState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.buttonTestConnection.isEnabled = true

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

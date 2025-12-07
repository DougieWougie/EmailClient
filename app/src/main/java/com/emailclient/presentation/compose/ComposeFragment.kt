package com.emailclient.presentation.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.emailclient.databinding.FragmentComposeBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment for composing new emails
 */
@AndroidEntryPoint
class ComposeFragment : Fragment() {

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    private val args: ComposeFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: If replyToEmailId is set, load original email and populate fields
        // TODO: Handle reply, reply all, and forward modes
        // TODO: Implement send functionality
        // TODO: Implement draft saving
        // TODO: Implement attachment handling

        binding.btnSend.setOnClickListener {
            sendEmail()
        }
    }

    private fun sendEmail() {
        // TODO: Validate fields
        // TODO: Call repository to send email
        // TODO: Navigate back on success
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

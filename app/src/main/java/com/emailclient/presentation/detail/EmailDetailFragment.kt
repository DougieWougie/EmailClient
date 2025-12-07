package com.emailclient.presentation.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.emailclient.databinding.FragmentEmailDetailBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment displaying email details
 */
@AndroidEntryPoint
class EmailDetailFragment : Fragment() {

    private var _binding: FragmentEmailDetailBinding? = null
    private val binding get() = _binding!!

    private val args: EmailDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmailDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: Load email details using args.emailId
        // TODO: Implement reply, reply all, forward actions
        // TODO: Display email body (handle HTML if needed)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

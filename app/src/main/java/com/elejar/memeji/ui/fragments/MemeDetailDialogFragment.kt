package com.elejar.memeji.ui.fragments

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.elejar.memeji.R
import com.elejar.memeji.data.Meme
import com.elejar.memeji.databinding.FragmentMemeDetailBinding
import com.elejar.memeji.ui.MainActivity
import com.elejar.memeji.viewmodel.MemeViewModel
import kotlinx.coroutines.launch

class MemeDetailDialogFragment : DialogFragment() {

    private var _binding: FragmentMemeDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MemeViewModel by activityViewModels()
    private var currentMeme: Meme? = null

    companion object {
        const val TAG = "MemeDetailDialog"
        private const val ARG_MEME = "arg_meme"

        fun newInstance(meme: Meme): MemeDetailDialogFragment {
            val fragment = MemeDetailDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_MEME, meme)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_MemesJi)
        currentMeme = arguments?.getParcelable(ARG_MEME)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (currentMeme == null) {
            dismiss()
            return
        }

        setupUI()
        setupListeners()
        observeViewModel()
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        // Add slide animation
        dialog?.window?.setWindowAnimations(android.R.style.Animation_Dialog)
    }

    private fun setupUI() {
        val meme = currentMeme!!
        binding.toolbar.title = meme.name
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        Glide.with(this)
            .load(meme.url)
            .placeholder(R.drawable.ic_placeholder_image)
            .error(R.drawable.ic_placeholder_image)
            .transition(DrawableTransitionOptions.withCrossFade())
            .fitCenter()
            .into(binding.imageViewDetailMeme)
    }

    private fun setupListeners() {
        binding.buttonDownload.setOnClickListener {
            currentMeme?.let { meme ->
                (activity as? MainActivity)?.downloadMeme(meme)
            }
        }

        binding.buttonBrowser.setOnClickListener {
            currentMeme?.let { meme ->
                (activity as? MainActivity)?.openUrlInBrowser(meme.url)
            }
        }

        binding.buttonShare.setOnClickListener {
            currentMeme?.let { meme ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.prepareMemeForSharing(meme)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.shareStatus.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandled()?.let { status ->
                binding.layoutShareProgress.isVisible = status.isLoading
                
                if (!status.isLoading) {
                    if (!status.isError && status.shareUri != null && status.mimeType != null) {
                        startShareIntent(status.shareUri, status.mimeType)
                        viewModel.clearShareIntentUri()
                    } else if (status.isError) {
                        if (!status.message.isNullOrBlank()) {
                            Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun startShareIntent(imageUri: Uri, mimeType: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting share intent", e)
            Toast.makeText(context, getString(R.string.share_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

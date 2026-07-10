package com.elejar.memeji.ui.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.elejar.memeji.R
import com.elejar.memeji.data.Meme
import com.elejar.memeji.databinding.FragmentHomeBinding
import com.elejar.memeji.ui.adapter.MemeAdapter
import com.elejar.memeji.ui.MainActivity
import com.elejar.memeji.viewmodel.MemeViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MemeViewModel by activityViewModels()
    private lateinit var memeAdapter: MemeAdapter
    private lateinit var layoutManager: StaggeredGridLayoutManager
    private var detailDialog: BottomSheetDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeToRefresh()
        setupRetryButton()
        observeViewModel()
    }

    private fun setupRetryButton() {
        binding.buttonRetry.setOnClickListener {
            viewModel.loadMemes(forceRefresh = true)
        }
    }

    private fun setupRecyclerView() {
        memeAdapter = MemeAdapter { meme ->
            showMemeDetail(meme)
        }

        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)

        binding.recyclerViewMemes.apply {
            adapter = memeAdapter
            layoutManager = this@HomeFragment.layoutManager
        }
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setColorSchemeColors(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary),
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondary),
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorTertiary)
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("HomeFragment", "Swipe to refresh triggered.")
            viewModel.loadMemes(forceRefresh = true)
        }
    }

    private fun observeViewModel() {
        viewModel.filteredMemes.observe(viewLifecycleOwner) { memes ->
            val isLoading = viewModel.isLoading.value ?: false
            val error = viewModel.error.value
            val hasData = !memes.isNullOrEmpty()

            binding.recyclerViewMemes.isVisible = !isLoading && error == null && hasData
            memeAdapter.submitList(memes)

            binding.emptyState.isVisible = !isLoading && error == null && !hasData
            if (binding.emptyState.isVisible) {
                updateNoMemesText()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val error = viewModel.error.value
            val hasData = !(viewModel.filteredMemes.value.isNullOrEmpty())

            // A linear indicator communicates a background refresh; the centered circular
            // indicator is reserved for the blocking, first-load state below.
            binding.loadingIndicator.isVisible = isLoading && hasData && !binding.swipeRefreshLayout.isRefreshing

            if (!isLoading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
            binding.progressBar.isVisible = isLoading && !binding.swipeRefreshLayout.isRefreshing && !hasData && error == null

            if (!isLoading) {
                binding.textViewError.isVisible = error != null
                binding.buttonRetry.isVisible = error != null
                binding.emptyState.isVisible = error == null && !hasData
                if (binding.textViewError.isVisible) {
                    binding.textViewError.text = error ?: getString(R.string.unknown_error)
                }
                if (binding.emptyState.isVisible) {
                    updateNoMemesText()
                }
                binding.recyclerViewMemes.isVisible = error == null && hasData
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            val isLoading = viewModel.isLoading.value ?: false
            val hasData = !(viewModel.filteredMemes.value.isNullOrEmpty())

            binding.textViewError.isVisible = error != null && !isLoading
            binding.buttonRetry.isVisible = error != null && !isLoading
            if (binding.textViewError.isVisible) {
                binding.textViewError.text = error ?: getString(R.string.unknown_error)
            }

            if (error != null && !isLoading) {
                binding.recyclerViewMemes.isVisible = false
                binding.emptyState.isVisible = false
                binding.progressBar.isVisible = false
            } else {
                binding.recyclerViewMemes.isVisible = !isLoading && hasData
                binding.emptyState.isVisible = !isLoading && !hasData
                if (binding.emptyState.isVisible) updateNoMemesText()
                binding.progressBar.isVisible = isLoading && !binding.swipeRefreshLayout.isRefreshing && !hasData
            }
        }

        viewModel.shareStatus.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandled()?.let { status ->
                updateShareProgress(status)
            }
        }
    }

    private fun updateNoMemesText() {
        val query = viewModel.searchQuery.value
        binding.textViewNoMemes.text = if (query.isNullOrBlank()) {
            getString(R.string.no_memes_found)
        } else {
            getString(R.string.no_memes_match_search, query)
        }
    }

    private fun showMemeDetail(meme: Meme) {
        detailDialog?.dismiss()

        val bottomSheet = BottomSheetDialog(requireContext(), R.style.ThemeOverlay_MemesJi_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_meme_detail, null)
        bottomSheet.setContentView(sheetView)

        val imageView = sheetView.findViewById<android.widget.ImageView>(R.id.bottomSheetMemeImage)
        val nameText = sheetView.findViewById<android.widget.TextView>(R.id.bottomSheetMemeName)
        val fullScreenBtn = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.bottomSheetButtonFullScreen)
        val downloadBtn = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.bottomSheetButtonDownload)
        val shareBtn = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.bottomSheetButtonShare)
        val browserBtn = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.bottomSheetButtonBrowser)

        nameText.text = meme.name

        Glide.with(this)
            .load(meme.url)
            .placeholder(R.drawable.ic_placeholder_image)
            .error(R.drawable.ic_placeholder_image)
            .transition(DrawableTransitionOptions.withCrossFade())
            .fitCenter()
            .into(imageView)

        fullScreenBtn.setOnClickListener {
            bottomSheet.dismiss()
            val dialog = MemeDetailDialogFragment.newInstance(meme)
            dialog.show(childFragmentManager, MemeDetailDialogFragment.TAG)
        }

        downloadBtn.setOnClickListener {
            (activity as? MainActivity)?.downloadMeme(meme)
        }

        shareBtn.setOnClickListener {
            shareMeme(meme)
        }

        browserBtn.setOnClickListener {
            (activity as? MainActivity)?.openUrlInBrowser(meme.url)
        }

        bottomSheet.setOnDismissListener {
            detailDialog = null
        }

        detailDialog = bottomSheet
        bottomSheet.show()
    }

    private fun shareMeme(meme: Meme) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.prepareMemeForSharing(meme)
        }
    }

    private fun updateShareProgress(status: MemeViewModel.ShareStatus) {
        detailDialog?.let { dialog ->
            val progressContainer = dialog.findViewById<android.widget.FrameLayout>(R.id.layoutShareProgress)
            val progressBar = dialog.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressBarDialogShare)

            progressContainer?.isVisible = status.isLoading
            progressBar?.isVisible = status.isLoading
        }

        if (!status.isLoading) {
            if (!status.isError && status.shareUri != null && status.mimeType != null) {
                startShareIntent(status.shareUri, status.mimeType)
                viewModel.clearShareIntentUri()
            } else if (status.isError) {
                if (!status.message.isNullOrBlank()) {
                    android.widget.Toast.makeText(context, status.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startShareIntent(imageUri: android.net.Uri, mimeType: String) {
        try {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_via)))
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error starting share intent", e)
            android.widget.Toast.makeText(context, getString(R.string.share_error), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val spanCount = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        layoutManager.spanCount = spanCount
    }

    override fun onPause() {
        super.onPause()
        detailDialog?.dismiss()
    }

    override fun onDestroyView() {
        detailDialog?.dismiss()
        detailDialog = null
        super.onDestroyView()
        binding.recyclerViewMemes.adapter = null
        _binding = null
    }
}

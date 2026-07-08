package com.elejar.memeji.ui.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.elejar.memeji.R
import com.elejar.memeji.data.Meme
import com.elejar.memeji.databinding.FragmentHomeBinding
import com.elejar.memeji.ui.adapter.MemeAdapter
import com.elejar.memeji.viewmodel.MemeViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MemeViewModel by activityViewModels()
    private lateinit var memeAdapter: MemeAdapter
    private lateinit var layoutManager: GridLayoutManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.swipeRefreshLayout.updatePadding(top = insets.top)
            view.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
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

        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3
        layoutManager = GridLayoutManager(context, spanCount)

        binding.recyclerViewMemes.apply {
            adapter = memeAdapter
            layoutManager = this@HomeFragment.layoutManager
            setHasFixedSize(true)
        }
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.md_theme_surface)
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

            binding.textViewNoMemes.isVisible = !isLoading && error == null && !hasData
            if (binding.textViewNoMemes.isVisible) {
                updateNoMemesText()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val error = viewModel.error.value
            val hasData = !(viewModel.filteredMemes.value.isNullOrEmpty())

            if (!isLoading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
            binding.progressBar.isVisible = isLoading && !binding.swipeRefreshLayout.isRefreshing && !hasData && error == null

            if (!isLoading) {
                binding.textViewError.isVisible = error != null
                binding.buttonRetry.isVisible = error != null
                binding.textViewNoMemes.isVisible = error == null && !hasData
                if (binding.textViewError.isVisible) {
                    binding.textViewError.text = error ?: getString(R.string.unknown_error)
                }
                if (binding.textViewNoMemes.isVisible) {
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
                binding.textViewNoMemes.isVisible = false
                binding.progressBar.isVisible = false
            } else {
                binding.recyclerViewMemes.isVisible = !isLoading && hasData
                binding.textViewNoMemes.isVisible = !isLoading && !hasData
                if (binding.textViewNoMemes.isVisible) updateNoMemesText()
                binding.progressBar.isVisible = isLoading && !binding.swipeRefreshLayout.isRefreshing && !hasData
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
        val dialog = MemeDetailDialogFragment.newInstance(meme)
        dialog.show(childFragmentManager, MemeDetailDialogFragment.TAG)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val spanCount = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3
        layoutManager.spanCount = spanCount
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewMemes.adapter = null
        _binding = null
    }
}

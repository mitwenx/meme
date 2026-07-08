package com.elejar.memeji.ui.fragments

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.elejar.memeji.R
import com.elejar.memeji.data.Meme
import com.elejar.memeji.databinding.FragmentCategoryMemesBinding
import com.elejar.memeji.ui.MainActivity
import com.elejar.memeji.ui.adapter.MemeAdapter
import com.elejar.memeji.viewmodel.MemeViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.launch

class CategoryMemesFragment : Fragment() {

    private var _binding: FragmentCategoryMemesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MemeViewModel by activityViewModels()
    private val args: CategoryMemesFragmentArgs by navArgs()
    private lateinit var memeAdapter: MemeAdapter
    private lateinit var layoutManager: StaggeredGridLayoutManager
    private var detailDialog: BottomSheetDialog? = null
    // Removed categoryDownloadDialog variable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryMemesBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupRetryButton()
        observeViewModel()
        viewModel.loadMemesForCategory(args.categoryName)
        viewModel.setSearchQuery(null)
    }


    private fun setupRecyclerView() {
        memeAdapter = MemeAdapter { meme ->
            showMemeDetailDialog(meme)
        }

        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)

        binding.recyclerViewCategoryMemes.apply {
            adapter = memeAdapter
            layoutManager = this@CategoryMemesFragment.layoutManager
        }
    }

    private fun setupRetryButton() {
        binding.buttonRetryCategoryMemes.setOnClickListener {
            viewModel.loadMemesForCategory(args.categoryName)
        }
    }

    private fun observeViewModel() {
        viewModel.filteredMemes.observe(viewLifecycleOwner) { memes ->
            val isLoading = viewModel.isLoading.value ?: false
            val error = viewModel.error.value
            val hasData = !memes.isNullOrEmpty()

            binding.progressBarCategoryMemes.isVisible = isLoading && !hasData
            binding.textViewNoCategoryMemes.isVisible = !isLoading && !hasData && error == null
            binding.textViewErrorCategoryMemes.isVisible = !isLoading && error != null
            binding.buttonRetryCategoryMemes.isVisible = !isLoading && error != null
            binding.recyclerViewCategoryMemes.isVisible = hasData && error == null

            memeAdapter.submitList(memes)

            if (binding.textViewNoCategoryMemes.isVisible) {
                updateNoMemesText()
            }
             if (binding.textViewErrorCategoryMemes.isVisible) {
                 binding.textViewErrorCategoryMemes.text = error ?: getString(R.string.unknown_error)
             }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
             val hasData = !(viewModel.filteredMemes.value.isNullOrEmpty())
             val error = viewModel.error.value

             binding.loadingIndicatorCategory.isVisible = isLoading
             binding.progressBarCategoryMemes.isVisible = isLoading && !hasData
              if (!isLoading) {
                  binding.textViewNoCategoryMemes.isVisible = !hasData && error == null
                  binding.textViewErrorCategoryMemes.isVisible = error != null
                  binding.buttonRetryCategoryMemes.isVisible = error != null
                  binding.recyclerViewCategoryMemes.isVisible = hasData && error == null
                  if(binding.textViewNoCategoryMemes.isVisible) updateNoMemesText()
                  if(binding.textViewErrorCategoryMemes.isVisible) binding.textViewErrorCategoryMemes.text = error ?: getString(R.string.unknown_error)
             } else {
                  if (!hasData) {
                      binding.textViewNoCategoryMemes.isVisible = false
                      binding.textViewErrorCategoryMemes.isVisible = false
                  }
             }
        }

         viewModel.error.observe(viewLifecycleOwner) { error ->
             val isLoading = viewModel.isLoading.value ?: false
             val hasData = !(viewModel.filteredMemes.value.isNullOrEmpty())
             val showErrorView = error != null && !isLoading

             binding.textViewErrorCategoryMemes.isVisible = showErrorView
              binding.buttonRetryCategoryMemes.isVisible = showErrorView
              binding.recyclerViewCategoryMemes.isVisible = !showErrorView || hasData
              binding.progressBarCategoryMemes.isVisible = isLoading && !hasData

              if (showErrorView) {
                  binding.textViewErrorCategoryMemes.text = error ?: getString(R.string.unknown_error)
                  binding.textViewNoCategoryMemes.isVisible = false
              } else if (!isLoading && !hasData) {
                  binding.textViewNoCategoryMemes.isVisible = true
                  updateNoMemesText()
              }
         }

          viewModel.shareStatus.observe(viewLifecycleOwner) { event ->
              event?.getContentIfNotHandled()?.let { status ->
                  updateShareProgress(status)
              }
          }

         // Removed observer for categoryDownloadStatus
    }

    private fun updateNoMemesText() {
        val query = viewModel.searchQuery.value
        binding.textViewNoCategoryMemes.text = if (query.isNullOrBlank()) {
            getString(R.string.no_memes_in_category, args.categoryName)
        } else {
            getString(R.string.no_memes_match_search, query)
        }
    }

     private fun showMemeDetailDialog(meme: Meme) {
          detailDialog?.dismiss()

          val bottomSheet = BottomSheetDialog(requireContext())
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
                      Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
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
              Log.e("CategoryMemesFragment", "Error starting share intent", e)
              Toast.makeText(context, getString(R.string.share_error), Toast.LENGTH_SHORT).show()
          }
      }

     // Removed showCategoryDownloadOptions function

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val spanCount = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        layoutManager.spanCount = spanCount
    }

     override fun onPause() {
        super.onPause()
        detailDialog?.dismiss()
        // Removed dismissal of categoryDownloadDialog
    }

    override fun onDestroyView() {
        detailDialog?.dismiss()
        detailDialog = null
        // Removed dismissal of categoryDownloadDialog
        super.onDestroyView()
        binding.recyclerViewCategoryMemes.adapter = null
        _binding = null
    }
}

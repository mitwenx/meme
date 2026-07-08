package com.elejar.memeji.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import com.elejar.memeji.R
import com.elejar.memeji.data.AppInfo
import com.elejar.memeji.data.CategoryItem
import com.elejar.memeji.data.Meme
import com.elejar.memeji.data.remote.RetrofitInstance
import com.elejar.memeji.repository.MemeRepository
import com.elejar.memeji.util.Event
import com.elejar.memeji.util.PreferencesHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MemeViewModel(application: Application) : AndroidViewModel(application) {

    private val _applicationContext = application.applicationContext

    private val repository = MemeRepository(
        RetrofitInstance.api,
        _applicationContext,
        RetrofitInstance.okHttpClient
    )

    private val _rawMemes = MutableLiveData<List<Meme>>()
    private val _totalMemeCount = MediatorLiveData<Int>().apply { value = 0 }
    val totalMemeCount: LiveData<Int> get() = _totalMemeCount

    private val _rawCategories = MutableLiveData<List<CategoryItem>>()

    private val _isCutieModeEnabled = MutableLiveData<Boolean>()
    val isCutieModeEnabled: LiveData<Boolean> get() = _isCutieModeEnabled

    private val _baseFilteredMemes = MediatorLiveData<List<Meme>>()

    private val _memesForCategory = MutableLiveData<List<Meme>?>()

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _availableUpdateInfo = MutableLiveData<AppInfo?>()
    val availableUpdateInfo: LiveData<AppInfo?> get() = _availableUpdateInfo

    private val _isCheckingForUpdate = MutableLiveData<Boolean>()
    val isCheckingForUpdate: LiveData<Boolean> get() = _isCheckingForUpdate

    private val _updateCheckResultEvent = MutableLiveData<Event<Int>>()
    val updateCheckResultEvent: LiveData<Event<Int>> get() = _updateCheckResultEvent

    private val _singleMemeDownloadStatus = MutableLiveData<String?>()
    val singleMemeDownloadStatus: LiveData<String?> get() = _singleMemeDownloadStatus

    private val _searchQuery = MutableLiveData<String?>()
    val searchQuery: LiveData<String?> get() = _searchQuery

    private val _categorySearchQuery = MutableLiveData<String?>()
    val categorySearchQuery: LiveData<String?> get() = _categorySearchQuery

    private val _filteredMemes = MediatorLiveData<List<Meme>>()
    val filteredMemes: LiveData<List<Meme>> get() = _filteredMemes

    private val _filteredCategories = MediatorLiveData<List<CategoryItem>>()
    val filteredCategories: LiveData<List<CategoryItem>> get() = _filteredCategories

    data class ShareStatus(
        val message: String?,
        val isLoading: Boolean,
        val isError: Boolean = false,
        val shareUri: Uri? = null,
        val mimeType: String? = null
    )
    private val _shareStatus = MutableLiveData<Event<ShareStatus>>()
    val shareStatus: LiveData<Event<ShareStatus>> get() = _shareStatus

    private var pendingDownloadAction: (() -> Unit)? = null


    init {
        _isCutieModeEnabled.value = PreferencesHelper.isCutieModeEnabled(_applicationContext)
        setupMediators()
        _totalMemeCount.addSource(_rawMemes) { memes ->
            _totalMemeCount.value = memes?.size ?: 0
        }
        loadMemes(forceRefresh = false)
    }

    private fun setupMediators() {
        _baseFilteredMemes.addSource(_rawMemes) { memes ->
            filterMemesByCutieMode(memes, _isCutieModeEnabled.value ?: true)
        }
        _baseFilteredMemes.addSource(_isCutieModeEnabled) { isEnabled ->
            filterMemesByCutieMode(_rawMemes.value, isEnabled)
        }

        _filteredMemes.addSource(_baseFilteredMemes) { memes ->
            filterMemesBySearch(memes, _searchQuery.value, _memesForCategory.value)
        }
        _filteredMemes.addSource(_searchQuery) { query ->
            filterMemesBySearch(_baseFilteredMemes.value, query, _memesForCategory.value)
        }
        _filteredMemes.addSource(_memesForCategory) { categoryMemes ->
            filterMemesBySearch(_baseFilteredMemes.value, _searchQuery.value, categoryMemes)
        }

        _filteredCategories.addSource(_rawCategories) { categories ->
            filterCategoriesBySearch(categories, _categorySearchQuery.value)
        }
        _filteredCategories.addSource(_categorySearchQuery) { query ->
            filterCategoriesBySearch(_rawCategories.value, query)
        }
    }

    private fun filterMemesByCutieMode(memes: List<Meme>?, cutieModeEnabled: Boolean) {
        val source = memes ?: emptyList()
        _baseFilteredMemes.value = if (cutieModeEnabled) {
            source.filter { meme ->
                !meme.tags.any { it.equals(MemeRepository.SENSITIVE_TAG, ignoreCase = true) }
            }
        } else {
            source
        }
        filterMemesBySearch(_baseFilteredMemes.value, _searchQuery.value, _memesForCategory.value)
    }

    private fun filterMemesBySearch(baseMemes: List<Meme>?, query: String?, categoryMemesSource: List<Meme>?) {
        val currentQuery = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val sourceList = if (!categoryMemesSource.isNullOrEmpty()) {
            val cutieFilteredCategoryMemes = if (_isCutieModeEnabled.value == true) {
                categoryMemesSource.filter { meme ->
                    !meme.tags.any { it.equals(MemeRepository.SENSITIVE_TAG, ignoreCase = true) }
                }
            } else {
                categoryMemesSource
            }
            cutieFilteredCategoryMemes

        } else {
            baseMemes ?: emptyList()
        }

        _filteredMemes.value = if (currentQuery.isNullOrBlank()) {
            sourceList
        } else {
            sourceList.filter { meme ->
                meme.name.lowercase().contains(currentQuery) ||
                        meme.tags.any { tag ->
                            !(_isCutieModeEnabled.value == true && tag.equals(MemeRepository.SENSITIVE_TAG, ignoreCase = true)) &&
                                    tag.lowercase().contains(currentQuery)
                        }
            }
        }
    }

    private fun filterCategoriesBySearch(categories: List<CategoryItem>?, query: String?) {
        val currentQuery = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val source = categories ?: emptyList()

        _filteredCategories.value = if (currentQuery.isNullOrBlank()) {
            source
        } else {
            source.filter { category ->
                category.name.lowercase().contains(currentQuery)
            }
        }
    }


    fun loadMemes(forceRefresh: Boolean = false) {
        if (_isLoading.value == true && !forceRefresh) {
            Log.d("MemeViewModel", "Load already in progress, skipping.")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            if (forceRefresh || _memesForCategory.value != null) {
                _memesForCategory.value = null
            }

            Log.d("MemeViewModel", "Fetching memes (forceRefresh=$forceRefresh)")

            val result = repository.getMemes(forceRefresh = forceRefresh)
            result.onSuccess { memeList ->
                _rawMemes.value = memeList
                _rawCategories.value = repository.getCategoriesWithImages()
            }.onFailure { throwable ->
                handleError(throwable, "Failed to load memes")
            }
            _isLoading.value = false
        }
    }

    fun loadMemesForCategory(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            if (repository.isCacheEmpty()) {
                val loadAllResult = repository.getMemes(forceRefresh = false)
                if (loadAllResult.isFailure) {
                    handleError(loadAllResult.exceptionOrNull()!!, "Failed to load memes before category filter")
                    _isLoading.value = false
                    _memesForCategory.value = emptyList()
                    return@launch
                }
            }

            val memes = repository.getMemesByCategory(category)
            _memesForCategory.value = memes
            _isLoading.value = false
        }
    }

    fun switchToHomeView() {
        if (_memesForCategory.value != null || _error.value != null) {
            _memesForCategory.value = null
            _error.value = null
        }
    }

    fun setSearchQuery(query: String?) {
        val trimmedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        if (_searchQuery.value != trimmedQuery) {
            _searchQuery.value = trimmedQuery
        }
    }

    fun setCategorySearchQuery(query: String?) {
        val trimmedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        if (_categorySearchQuery.value != trimmedQuery) {
            _categorySearchQuery.value = trimmedQuery
        }
    }

    fun updateCutieMode(enabled: Boolean) {
        if (_isCutieModeEnabled.value != enabled) {
            _isCutieModeEnabled.value = enabled
            PreferencesHelper.setCutieModeEnabled(_applicationContext, enabled)
        }
    }

    fun checkForUpdates() {
        if (_isCheckingForUpdate.value == true) {
            return
        }

        viewModelScope.launch {
            _isCheckingForUpdate.value = true
            _availableUpdateInfo.value = null
            val result = repository.getAppUpdateInfo()
            result.onSuccess { info ->
                if (info.showDownload) {
                    _availableUpdateInfo.value = info
                    _updateCheckResultEvent.value = Event(R.string.update_available)
                } else {
                    _updateCheckResultEvent.value = Event(R.string.no_update_available)
                }
            }.onFailure { throwable ->
                Log.e("MemeViewModel", "Failed to fetch app update info", throwable)
                // FIXED: Updated to match ID in strings.xml
                _updateCheckResultEvent.value = Event(R.string.check_for_update_desc_error)
            }
            _isCheckingForUpdate.value = false
        }
    }

    suspend fun prepareMemeForSharing(meme: Meme) {
        if (_isCutieModeEnabled.value == true && meme.tags.any { it.equals(MemeRepository.SENSITIVE_TAG, ignoreCase = true) }) {
            _shareStatus.postValue(Event(ShareStatus("Cannot share this meme in Cutie Mode", isLoading = false, isError = true)))
            return
        }

        _shareStatus.postValue(Event(ShareStatus(getString(R.string.preparing_share), isLoading = true)))
        val downloadResult = repository.downloadImageToCache(meme.url)

        downloadResult.onSuccess { fileFromCache ->
            val shareableData = repository.getShareableUri(meme, fileFromCache)
            if (shareableData != null) {
                _shareStatus.postValue(Event(ShareStatus(
                    message = getString(R.string.share_via),
                    isLoading = false,
                    shareUri = shareableData.uri,
                    mimeType = shareableData.mimeType
                )))
            } else {
                _shareStatus.postValue(Event(ShareStatus(getString(R.string.error_preparing_share), isLoading = false, isError = true)))
            }
        }.onFailure {
            _shareStatus.postValue(Event(ShareStatus(getString(R.string.error_preparing_share) + ": ${it.localizedMessage}", isLoading = false, isError = true)))
        }
    }

    fun clearShareIntentUri() {
        _shareStatus.value = Event(ShareStatus(null, isLoading = false))
    }

    fun downloadMeme(meme: Meme) {
        if (_isCutieModeEnabled.value == true && meme.tags.any { it.equals(MemeRepository.SENSITIVE_TAG, ignoreCase = true) }) {
            _singleMemeDownloadStatus.value = "Cannot download this meme in Cutie Mode"
            clearStatusAfterDelay(_singleMemeDownloadStatus, 4000L)
            return
        }
        viewModelScope.launch {
            _singleMemeDownloadStatus.value = getString(R.string.download_started, meme.name)
            val result = repository.downloadMemeDirect(meme)
            result.onSuccess { path ->
                _singleMemeDownloadStatus.value = getString(R.string.download_complete)
                clearStatusAfterDelay(_singleMemeDownloadStatus, 3000L)
            }.onFailure { e ->
                _singleMemeDownloadStatus.value = getString(R.string.download_failed_single, meme.name)
                clearStatusAfterDelay(_singleMemeDownloadStatus, 5000L)
            }
        }
    }

    fun clearSingleMemeDownloadStatus() {
        _singleMemeDownloadStatus.value = null
    }

    private fun clearStatusAfterDelay(liveData: MutableLiveData<String?>, delayMillis: Long) {
        viewModelScope.launch {
            delay(delayMillis)
            liveData.postValue(null)
        }
    }

    fun setPendingDownloadAction(action: () -> Unit) {
        pendingDownloadAction = action
    }

    fun executePendingDownloadAction() {
        pendingDownloadAction?.invoke()
        pendingDownloadAction = null
    }

    fun clearPendingDownloadAction() {
        pendingDownloadAction = null
    }

    private fun handleError(throwable: Throwable, contextMessage: String) {
        Log.e("MemeViewModel", "$contextMessage: ${throwable.message}", throwable)
        val userMessage = throwable.localizedMessage ?: getString(R.string.unknown_error)
        _error.value = "$contextMessage: $userMessage"
    }

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return _applicationContext.getString(resId, *formatArgs)
    }
}

package com.psimandan.neuread.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psimandan.neuread.data.model.EBookFile
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.data.repository.EBookRepository
import com.psimandan.neuread.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LibraryScreenViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val fileRepository: EBookRepository
) : ViewModel() {

    private val _libraryBooks = MutableStateFlow<List<NeuReadBook>>(emptyList())
    val libraryBooks = _libraryBooks.asStateFlow()

    private val _selectedBook = MutableStateFlow<NeuReadBook?>(null)
    val selectedBook = _selectedBook.asStateFlow()

    data class LibraryScreenUIState(
        val loading: Boolean = false,
        val showNewBookPicker: Boolean = false,
        val filterText: String = "",
        val downloadProgress: Map<String, Float> = emptyMap()
    )

    private val _viewState = MutableStateFlow(LibraryScreenUIState())
    val viewState: StateFlow<LibraryScreenUIState> get() = _viewState.asStateFlow()

    private val downloadTrackingJobs = mutableMapOf<String, Job>()

    fun loadBooks(workManager: androidx.work.WorkManager) {
        viewModelScope.launch {
            launch {
                libraryRepository.getLibraryBooks().collect { books ->
                    _libraryBooks.value = books

                    // Track downloads for these books
                    updateDownloadTracking(books, workManager)
                }
            }
            _selectedBook.emit(libraryRepository.getSelectedBook())
        }
    }

    private fun updateDownloadTracking(books: List<NeuReadBook>, workManager: androidx.work.WorkManager) {
        val currentBookIds = books.map { it.id }.toSet()
        
        // Stop tracking books that are no longer in the library
        val jobsToRemove = downloadTrackingJobs.keys.filter { it !in currentBookIds }
        jobsToRemove.forEach { id ->
            downloadTrackingJobs[id]?.cancel()
            downloadTrackingJobs.remove(id)
            _viewState.value = _viewState.value.copy(
                downloadProgress = _viewState.value.downloadProgress.toMutableMap().apply {
                    remove(id)
                }
            )
        }

        // Start tracking new books
        books.forEach { book ->
            if (book.id !in downloadTrackingJobs) {
                downloadTrackingJobs[book.id] = viewModelScope.launch {
                    Timber.d("Starting download tracking for book: ${book.id}")
                    workManager.getWorkInfosByTagFlow("download_${book.id}").collect { workInfos ->
                        val runningWork = workInfos.firstOrNull { 
                            it.state == androidx.work.WorkInfo.State.RUNNING || 
                            it.state == androidx.work.WorkInfo.State.ENQUEUED 
                        }
                        val progress = runningWork?.progress?.getFloat("progress", 0f)
                        
                        _viewState.value = _viewState.value.copy(
                            downloadProgress = _viewState.value.downloadProgress.toMutableMap().apply {
                                if (runningWork != null && runningWork.state == androidx.work.WorkInfo.State.RUNNING) {
                                    put(book.id, progress ?: 0f)
                                } else if (runningWork != null && runningWork.state == androidx.work.WorkInfo.State.ENQUEUED) {
                                    put(book.id, 0f)
                                } else {
                                    remove(book.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    fun onSelectBook(book: NeuReadBook) {
        viewModelScope.launch {
            libraryRepository.selectBook(book.id)
            _selectedBook.emit(libraryRepository.getSelectedBook())
        }
    }

    fun onShowNewBookPicker(show: Boolean) {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(showNewBookPicker = show)
        }
    }

    fun onFilterWithText(text: String) {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(filterText = text)
        }
    }

    fun onUnselectBook() {
        viewModelScope.launch {
            libraryRepository.unselectBook()
            _selectedBook.emit(null)
        }
    }

    fun loadEBookFromUri(uri: Uri, onLoaded:(EBookFile?)-> Unit) {
        viewModelScope.launch {
            _viewState.emit(_viewState.value.copy(loading = true))
            onLoaded(fileRepository.getEBookFileFromUri(uri))
            _viewState.emit(_viewState.value.copy(loading = false))
        }


    }

    fun loadEBookFromClipboard(onLoaded:(EBookFile?)-> Unit) {
        viewModelScope.launch {
            _viewState.emit(_viewState.value.copy(loading = true))
            onLoaded(fileRepository.getEbookFileFromClipboard())
            _viewState.emit(_viewState.value.copy(loading = false))
        }
    }
}

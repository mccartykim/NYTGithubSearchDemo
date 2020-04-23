package com.mccartykim.nytgithubsearchdemo

import android.util.Log
import com.mccartykim.nytgithubsearchdemo.search.ErrorListing
import com.mccartykim.nytgithubsearchdemo.search.GithubSearch
import com.mccartykim.nytgithubsearchdemo.search.Listing
import com.mccartykim.nytgithubsearchdemo.search.RepoListing
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException

object MainViewModel {
    val searcher = GithubSearch()

    private val viewModelSubject: PublishSubject<ViewModelEvents> = PublishSubject.create()
    val viewModelObservable: Observable<ViewModelEvents> = viewModelSubject.observeOn(AndroidSchedulers.mainThread())
    val viewSubject: PublishSubject<MainActivityEvents> = PublishSubject.create()
    val disposable = CompositeDisposable()
    private lateinit var uiScope: CoroutineScope

    // View data
    private var submitEnabled = false
    private var query: String = ""
    private var searchResults: List<Listing>  = emptyList()

    fun onActivityCreated(coroutineScope: CoroutineScope) {
        uiScope = coroutineScope
        disposable.addAll(
            viewSubject.ofType(SubmitButtonPressed::class.java).subscribe(this::submitPressed),
            viewSubject.ofType(SearchbarSubmit::class.java).subscribe(this::submitFromKeyboard),
            viewSubject.ofType(ListingClicked::class.java).subscribe(this::resultItemClicked),
            viewSubject.ofType(SearchbarTextChanged::class.java).subscribe(this::searchTextChanged)
        )
    }

    private suspend fun submit(searchBarText: String) {
       searchResults = if (searchBarText.isNotBlank()) {
            try {
                withContext(Dispatchers.IO) { searcher.getReposByStars(searchBarText) }
            } catch (e: IOException) {
                Log.d("MainViewModel", e.message?: "IO Exception with no message")
                listOf(ErrorListing)
            }
        } else {
            emptyList()
        }
        viewModelSubject.onNext(NewResults(searchResults))
        query = ""
        viewModelSubject.onNext(ClearSearchbar)
        viewModelSubject.onNext(DisableSubmit)
        submitEnabled = false
    }

    private fun submitPressed(event: SubmitButtonPressed) {
        if (query.isNotBlank() && submitEnabled) {
            uiScope.launch { submit(query) }
        }
    }

    private fun submitFromKeyboard(event: SearchbarSubmit) {
        if (query.isNotBlank() && submitEnabled) {
            uiScope.launch { submit(query) }
        }
    }

    private fun searchTextChanged(event: SearchbarTextChanged) {
        when {
            event.query.isNullOrBlank() -> {
                query = ""
                submitEnabled = false
                viewModelSubject.onNext(DisableSubmit)
            }
            else -> {
                query = event.query.trim()
                // TODO maybe I need to make submitEnabled into its own behavior subject
                submitEnabled = true
                viewModelSubject.onNext(EnableSubmit)
            }
        }
    }

    private fun resultItemClicked(event: ListingClicked) {
        when(val listing = searchResults[event.position]) {
            is RepoListing -> viewModelSubject.onNext(LoadGithubPage(listing.html_url))
            else -> { /* ignore */ }
        }
    }

    fun onActivityDestroyed() {
        disposable.clear()
        submitEnabled = false
        query = ""
        searchResults = emptyList()
    }
}


sealed class ViewModelEvents
class NewResults(val results: List<Listing>): ViewModelEvents()
object ClearSearchbar: ViewModelEvents()
object EnableSubmit: ViewModelEvents()
object DisableSubmit: ViewModelEvents()
class LoadGithubPage(val url: String): ViewModelEvents()

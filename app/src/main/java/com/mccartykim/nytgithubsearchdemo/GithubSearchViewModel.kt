package com.mccartykim.nytgithubsearchdemo

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.mccartykim.nytgithubsearchdemo.search.*
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okio.IOException

class GithubSearchViewModel: BaseObservable() {
    @VisibleForTesting
    var searcher = GithubSearch()

    val viewModelSubject: PublishSubject<ViewModelEvent> = PublishSubject.create()

    @get: Bindable
    var suggestedOrgs: List<String> = emptyList()
        set(value) {
            if (value != field) {
                field = value
                notifyPropertyChanged(BR.suggestedOrgs)
            }
        }

    // View data
    @get:Bindable
    var submitEnabled = false

    @get:Bindable
    var isLoading = false
    set(value) {
        if (field != value) {
            field = value
            notifyPropertyChanged(BR.loading)
        }
    }

    @get:Bindable
    var query: String = ""
        @Bindable
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.query)
                onQueryChanged()
            }
        }


    @get:Bindable
    var searchResults: List<Listing>  = emptyList()
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.searchResults)
            }
        }

    fun submit() {
        if (query.isNotBlank()) {
            val trimmedAndLowercase = query.trim().toLowerCase()
            if (validateGithubUsername(trimmedAndLowercase)) {
                GlobalScope.launch { submit(trimmedAndLowercase) }
            } else {
                viewModelSubject.onNext(Warning("Github names must contain only alphanumeric characters and hyphens, and be under 40 characters"))
            }
        }
    }

    private fun onQueryChanged() {
        when (validateGithubUsername(query.toLowerCase().trim())) {
            true -> submitEnabled = true
            false -> {
                submitEnabled = false
            }
        }
        notifyPropertyChanged(BR.submitEnabled)
    }

    // Public domain regex found at https://github.com/shinnn/github-username-regex while looking up login validation
    val githubLoginRegex = Regex("""[a-z\d](?:[a-z\d]|-(?=[a-z\d])){0,38}""")
    @VisibleForTesting
    fun validateGithubUsername(name: String): Boolean = name.matches(githubLoginRegex)

    suspend fun loadSuggestions() {
        suggestedOrgs = searcher.getMostPopularOrgs()
            .map { it.login }
    }

    @VisibleForTesting
    suspend fun submit(searchBarText: String) {
        isLoading = true
        searchResults = when {
           searchBarText.isNotBlank() -> {
               try {
                   val results = searcher.getReposByStars(searchBarText)
                   if (results.isEmpty()) listOf(EmptyListing) else results
               } catch (e: IOException) {
                   Log.d("MainViewModel", e.message?: "IO Exception with no message")
                   listOf(ErrorListing)
               }
           }
           else -> {
               emptyList()
           }
        }
        isLoading = false
        val firstResult = searchResults.firstOrNull()
        if (firstResult is RepoListing) {
            viewModelSubject.onNext(PreloadTopLink(firstResult.html_url))
            query = ""
            submitEnabled = false
        }
    }

    fun resultItemClicked(adapterPosition: Int) {
        when(val listing = searchResults.getOrNull(adapterPosition)) {
            is RepoListing -> viewModelSubject.onNext(LoadGithubPage(listing.html_url))
            else -> { /* ignore */ }
        }
    }
}


sealed class ViewModelEvent
class Warning(val warning: String): ViewModelEvent()
class PreloadTopLink(val url: String): ViewModelEvent()
class LoadGithubPage(val url: String): ViewModelEvent()

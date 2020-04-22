package com.mccartykim.nytgithubsearchdemo

import android.content.ComponentName
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.*
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mccartykim.nytgithubsearchdemo.search.GithubSearch
import com.mccartykim.nytgithubsearchdemo.search.RepoListing
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private val searchBar by lazy { findViewById<EditText>(R.id.org_searchbar) }
    private val submitBtn by lazy { findViewById<Button>(R.id.submit_btn) }

    private var searchResults: MutableList<RepoListing> = mutableListOf()
    private val resultsRecyclerView by lazy { findViewById<RecyclerView>(R.id.results_recycler_view)}

    private val searcher = GithubSearch()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO warmup chrome tab
        resultsRecyclerView.setHasFixedSize(false)
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = SearchResultsAdapter(searchResults)
    }

    override fun onResume() {
        super.onResume()
        // super clean that up! use coroutines maybe! Or RxJava! But coroutines sound cool!
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitBtn.performClick()
                true
            } else {
                false
            }
        }
        searchBar.addTextChangedListener(afterTextChanged = { queryText ->
            submitBtn.isEnabled = !queryText.isNullOrBlank()
        })
        submitBtn.setOnClickListener {
            searchResults.clear()
            resultsRecyclerView?.adapter?.notifyDataSetChanged()
            val query = searchBar.text.trim()
            launch { getTextResults(query) }
        }
    }

    private suspend fun getTextResults(query: CharSequence) {
        val results = withContext(Dispatchers.IO) {
            // in an IO thread
            searcher.getReposByStars(query.trim());
        }
        searchResults.clear()
        searchResults.addAll(results)
        resultsRecyclerView.adapter?.notifyDataSetChanged()
    }

    // TODO Set up Chrome custom view or webview
}

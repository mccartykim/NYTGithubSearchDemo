package com.mccartykim.nytgithubsearchdemo

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import com.mccartykim.nytgithubsearchdemo.search.GithubSearch
import com.mccartykim.nytgithubsearchdemo.search.RepoListing
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private val searchBar by lazy { findViewById<EditText>(R.id.org_searchbar) }
    private val submitBtn by lazy { findViewById<Button>(R.id.submit_btn) }

    private val textView by lazy { findViewById<TextView>(R.id.org_results) }

    private val searcher = GithubSearch()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        // super mvp, clean that up! use coroutines maybe! Or RxJava! But coroutines sound cool!
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
            val query = searchBar.text.trim()
            launch { getTextResults(query) }
        }
    }

    suspend fun getTextResults(query: CharSequence) {
        val results = withContext(Dispatchers.IO) {
            // in an IO thread
            searcher.getReposByStars(query.trim());
        }
        // Back on the main thread, no runOnUiThread required!
        if (results != null) textView.text = results
            .map { "${it.stargazers_count} ${it.name}: ${it.html_url}" }
            .joinToString("\n")
        }

    // TODO Set up Chrome custom view or webview
}

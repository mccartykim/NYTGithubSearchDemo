package com.mccartykim.nytgithubsearchdemo

import android.content.ComponentName
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.*
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mccartykim.nytgithubsearchdemo.search.RepoListing
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private val searchBar by lazy { findViewById<EditText>(R.id.org_searchbar) }
    private val submitBtn by lazy { findViewById<Button>(R.id.submit_btn) }
    private val resultsRecyclerView by lazy { findViewById<RecyclerView>(R.id.results_recycler_view)}
    private lateinit var recyclerViewAdapter: SearchResultsAdapter

    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null

    private val customTabsIntent by lazy {
        CustomTabsIntent.Builder()
            .setToolbarColor(Color.rgb(201, 66, 128))
            .setShowTitle(true)
            .setStartAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_in_left)
            .setExitAnimations(this, android.R.anim.slide_out_right, android.R.anim.slide_out_right)
            .build()
    }

    private val disposable = CompositeDisposable()
    private val mainActivitySubject: PublishSubject<MainActivityEvents> = MainViewModel.viewSubject


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        launch { warmupChrome() }

        MainViewModel.onActivityCreated(this)

        resultsRecyclerView.setHasFixedSize(false)
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)

        recyclerViewAdapter = SearchResultsAdapter()
        resultsRecyclerView.adapter = recyclerViewAdapter
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mainActivitySubject.onNext(SearchbarSubmit)
                true
            } else {
                false
            }
        }
        searchBar.addTextChangedListener(afterTextChanged = { mainActivitySubject.onNext(SearchbarTextChanged(it?.toString()))} )
        submitBtn.setOnClickListener { mainActivitySubject.onNext(SubmitButtonPressed)}

        disposable.addAll(
            MainViewModel.viewModelObservable.ofType(NewResults::class.java).subscribe {
                recyclerViewAdapter.dataSet = it.results
                recyclerViewAdapter.notifyDataSetChanged()
                it.results.firstOrNull()?.let {
                    if (it is RepoListing) customTabsSession?.mayLaunchUrl(Uri.parse(it.html_url), Bundle.EMPTY, emptyList())
                }
            },
            MainViewModel.viewModelObservable.ofType(ClearSearchbar::class.java).subscribe { searchBar.text.clear() },
            MainViewModel.viewModelObservable.ofType(EnableSubmit::class.java).subscribe { submitBtn.isEnabled = true },
            MainViewModel.viewModelObservable.ofType(DisableSubmit::class.java).subscribe { submitBtn.isEnabled = false },
            MainViewModel.viewModelObservable.ofType(LoadGithubPage::class.java)
                .subscribe { customTabsIntent.launchUrl(this, Uri.parse(it.url)) }
        )
    }

    suspend fun startChromeServiceConnectionAndWarmup(tries: Int = 3) {
        if (tries <= 0) {
            return
        }
        val customTabsServiceConnection = object: CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: ComponentName,
                client: CustomTabsClient
            ) {
                customTabsClient = client
                customTabsClient?.warmup(0)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                customTabsClient = null
            }
        }
        CustomTabsClient.bindCustomTabsService(this, "com.android.chrome", customTabsServiceConnection)
        if (customTabsClient != null) {
            customTabsClient?.let { customTabsSession = it.newSession(CustomTabsCallback()) }
            return
        } else {
            startChromeServiceConnectionAndWarmup(tries - 1)
        }
    }

    suspend fun warmupChrome() {
        startChromeServiceConnectionAndWarmup()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        MainViewModel.onActivityDestroyed()
    }
}

sealed class MainActivityEvents
object SubmitButtonPressed: MainActivityEvents()
object SearchbarSubmit: MainActivityEvents()
class ListingClicked(val position: Int): MainActivityEvents()
class SearchbarTextChanged(val query: String?): MainActivityEvents()

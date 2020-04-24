package com.mccartykim.nytgithubsearchdemo

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.*
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mccartykim.nytgithubsearchdemo.databinding.ActivityMainBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null

    private val customTabsIntent: CustomTabsIntent by lazy {
        // try to use our session, assuming it set up correctly, otherwise create a default builder
        (customTabsSession?.let {CustomTabsIntent.Builder(it)}?: CustomTabsIntent.Builder())
            .setToolbarColor(ContextCompat.getColor(this, R.color.colorCustomMagenta))
            .setShowTitle(true)
            .setStartAnimations(this, android.R.anim.fade_in, android.R.anim.fade_out)
            .setExitAnimations(this, android.R.anim.fade_in, android.R.anim.fade_out)
            .build()
    }

    private val disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.viewmodel = GithubSearchViewModel

        launch { GithubSearchViewModel.loadSuggestions() }
        launch { warmupChrome() }

        findViewById<RecyclerView>(R.id.results_recycler_view).apply {
            setHasFixedSize(false)
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = SearchResultsRecyclerViewAdapter()
        }

        // I wrote a draft primarily around RxJava but then decided to give data bindings a try
        // I don't like having intents live outside of a view if I can help it, so I kept the observable
        // for this on the philosophy that the view model could serve this to other url-consuming/rendering views
        disposable.addAll(
            GithubSearchViewModel.viewModelObservable
                .subscribe {
                    when (it) {
                        is PreloadTopLink -> customTabsSession?.mayLaunchUrl(Uri.parse(it.url), Bundle.EMPTY, listOf())
                        is LoadGithubPage -> customTabsIntent.launchUrl(this, Uri.parse(it.url))
                        is Warning -> Toast.makeText(this, it.warning, Toast.LENGTH_LONG).show()
                    }
                }
        )
    }

    // attempt to warm up the ChromeCustomTabsService
    private suspend fun startChromeServiceConnectionAndWarmup(tries: Int = 3) {
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

    private suspend fun warmupChrome() {
        startChromeServiceConnectionAndWarmup()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }
}


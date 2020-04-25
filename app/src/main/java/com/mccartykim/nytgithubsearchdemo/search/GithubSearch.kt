package com.mccartykim.nytgithubsearchdemo.search

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.squareup.moshi.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.concurrent.atomic.AtomicBoolean

class GithubSearch(private val httpClient: OkHttpClient = OkHttpClient()) {
    // Since this is using a hardcoded string I know is a valid URL,
    // and I'm in a hurry, I'd rather this fatally crash with a doublebang so I can fix it now
    // Were this based on arbitrary or third-party info, I'd be more disciplined
    private val baseUrl: HttpUrl = GITHUB_HOST_BASE_URL.toHttpUrlOrNull()!!

    /**
     * Get organizations that own the most popular repositories
     * NB: Limit 100 candidate repos, pagination is not implemented
     *
     * @param count number of repositories to look at, and then filter for those owned by organizations
     * @return list of github orgs, which are a type of user
     */
    suspend fun getMostPopularOrgs(count: Int = 100): List<GithubUser> {
        val url = buildMostPopularReposSearchPath(count)
        Log.d("URL_MostpopularOrgs", url.toString())
        val request = Request.Builder().url(url).build()

        val moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<RepoSearchResults> = RepoSearchResultsJsonAdapter(moshi)

        val response = executeRequest(request)

        val repos = getModelFromResponse(adapter, response)?.items?:emptyList()
        return repos.map { it.owner }.filter { it.type == UserType.Organization }.distinctBy { it.login }
    }


    // blocking get, should not be run on main thread
    // For production, I'd want better logging
    suspend fun getReposByStars(orgName: CharSequence, count: Int = 3): List<RepoListing> {
        val url = buildMostStarsFromOrgSearchPath(orgName, count)
        Log.d("URL_MostpopRepoForOrg", url.toString())
        val request = Request.Builder().url(url).build()

        val moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<RepoSearchResults> = RepoSearchResultsJsonAdapter(moshi)

        val response = executeRequest(request)

        return getModelFromResponse(adapter, response)?.items?: emptyList()
    }

    private var lastCall: Long = System.currentTimeMillis() - DELAY
    private val requestQueued: AtomicBoolean = AtomicBoolean(false)

    @VisibleForTesting
    suspend fun executeRequest(request: Request): Response? {
        val timeSinceLastCall = System.currentTimeMillis() - lastCall
        val response = when {
            timeSinceLastCall > DELAY && !requestQueued.get() -> {
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                lastCall = System.currentTimeMillis()
                response
            }
            requestQueued.compareAndSet(false, true) -> {
                delay(DELAY - timeSinceLastCall)
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                requestQueued.compareAndSet(true, false)
                lastCall = System.currentTimeMillis()
                response
            }
            else -> {
                null
            }
        }
        return response
    }

    /**
     * @return T associated with JsonAdapter, or null if status code says resource isn't found
     */
    private suspend fun <T>getModelFromResponse(adapter: JsonAdapter<T>, response: Response?): T? {
        response.use {
            val body = response?.body
            return withContext(Dispatchers.IO) {
                when {
                    response == null -> throw IOException("-1 rate limited")
                    response.isSuccessful && body != null -> {
                        body.use {
                            JsonReader.of(it.source()).use { reader ->
                                adapter.fromJson(reader)
                            }
                                ?: throw JsonDataException("Github Search Result somehow doesn't have an items field")
                        }
                    }
                    response.headers.names()
                        .contains("status") && response.headers["status"]!!.startsWith("4") -> null
                    else -> throw IOException(
                        response.headers["status"] ?: "-1 No status code found"
                    )
                }
            }
        }
    }

    /* Why I'm using a search instead of the restful API to list an Org's repos
     * Github offers the data I want in the order I need via this API
     * It's part of the API, and with a specific org's name, it's a reliable way to get a specific result, unlike a keyword search
     * Some orgs might have so many repos that pagination is an issue, which is tedious to deal with if we only ever want
     a few listings
     */
    private fun buildMostStarsFromOrgSearchPath(orgName: CharSequence, count: Int = 3): HttpUrl {
        return baseUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment("repositories")
            .addQueryParameter("q", "org:$orgName")
            .addQueryParameter("sort", "stars")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", count.toString())
            .build()
    }

    // Dead code left to show earlier approach, before I read more about the options with Github's search API
    // I realized it's better to trust the API to deliver correct and sorted repos, assuming I rate-limit my requests

    // blocking get, will run offthread via coroutines
    // I'm aware that OkHttp itself offers asynchronous access!
    // I just always wanted to try coroutines
    // For production, I'd want better logging
    fun getRepos(orgName: String): List<RepoListing>? {
        val url = buildOrgReposPath(orgName) ?: run { return null }
        val request = Request.Builder().url(url).build()

        val type = Types.newParameterizedType(List::class.java, RepoListing::class.java)
        val adapter = Moshi.Builder().build().adapter<List<RepoListing>>(type)

        val response = httpClient.newCall(request).execute()
        return when {
            response.isSuccessful -> {
                response.body?.use {
                    adapter.fromJson(it.source())
                }
            }
            else -> null
        }
    }

    // Dead code left to show earlier approach, before I read more about the options with Github's search API
    // I realized it's better to trust the API to deliver correct and sorted repos, assuming I rate-limit my requests
    private fun buildOrgReposPath(orgName: String): HttpUrl? =
        baseUrl.newBuilder()
            .addPathSegment("orgs")
            .addEncodedPathSegment(orgName)
            .addPathSegment("repos")
            .build()

    private fun buildMostPopularReposSearchPath(count: Int): HttpUrl =
        baseUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment("repositories")
            .addQueryParameter("q", "stars:>1000")
            .addQueryParameter("sort", "stars")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", count.toString())
            .build()

    companion object {
        const val GITHUB_HOST_BASE_URL = "https://api.github.com/"
        // Github Rate Limit is 10 requests per minute, with a period of 6000 ms,
        // this is a cooldown period to get the average closer to that
        const val DELAY = 2000L
    }
}

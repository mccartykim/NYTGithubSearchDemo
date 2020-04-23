package com.mccartykim.nytgithubsearchdemo.search

import com.squareup.moshi.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException

/**
 *
 */
// TODO If I add dagger, this would be a good place to demo it
class GithubSearch(private val httpClient: OkHttpClient = OkHttpClient()) {
    // Since this is using a hardcoded string I know is a valid URL,
    // and I'm in a hurry, I'd rather this fatally crash with a doublebang so I can fix it now
    // Were this based on arbitrary or third-party info, I'd be more disciplined
    private val baseUrl: HttpUrl = GITHUB_HOST_BASE_URL.toHttpUrlOrNull()!!


    // Idea: predictive search for orgnames? Limit reqs to less than 10/minute, perhaps using interceptor. Might be a nice RxJava idea
    // We can't search by prefix, but we could instead get the 1,000 most popular orgs, and populate a list or trie or something like that with them


    // blocking get, should not be run on main thread
    // For production, I'd want better logging
    // TODO get different types of error to the view, IE no results, network error
    suspend fun getReposByStars(orgName: CharSequence, count: Int = 3): List<RepoListing> {
        val url = buildMostStarsSearchPath(orgName, count)
        val request = Request.Builder().url(url).build()

        val moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<RepoSearchResults> = moshi.adapter(
            RepoSearchResults::class.java)

        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute()
        }

        return when {
            response.isSuccessful -> {
                response.body?.use {
                    JsonReader.of(it.source()).use {
                        withContext(Dispatchers.IO) { adapter.fromJson(it) }?.items ?: throw JsonDataException("Github Repo Search somehow doesn't have an items field")
                    }
                }?: throw IOException("Response was successful but body is null, which should not happen with this API")
            }
            else -> throw IOException(response.headers["status"]?: "-1 No status code found")
        }
    }

    /* Why I'm using a search instead of the restful API to list an Org's repos
     * Github offers the data I want in the order I need via this API
     * It's part of the API, and with a specific org's name, it's a reliable way to get a specific result, unlike a keyword search
     * Some orgs might have so many repos that pagination is an issue, which is tedious to deal with if we only ever want
     a few listings
     */
    private fun buildMostStarsSearchPath(orgName: CharSequence, count: Int = 3): HttpUrl {
        return baseUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment("repositories")
            .addQueryParameter("q", "org:$orgName")
            .addQueryParameter("sort", "stars")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", count.toString())
            .build()
    }

    // blocking get, will run offthread via coroutines
    // I'm aware that OkHttp itself offers asynchronous access!
    // I just always wanted to try coroutines
    // For production, I'd want better logging
    @Deprecated("earlier implementation")
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
    private fun buildOrgReposPath(orgName: String): HttpUrl? {
        return baseUrl.newBuilder()
            .addPathSegment("orgs")
            .addPathSegment(orgName)
            .addPathSegment("repos")
            .build()
    }

    companion object {
        const val GITHUB_HOST_BASE_URL = "https://api.github.com/"
    }
}
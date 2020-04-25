package com.mccartykim.nytgithubsearchdemo

import com.mccartykim.nytgithubsearchdemo.search.GithubSearch
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException
import okio.buffer
import okio.source
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test
import kotlin.system.measureTimeMillis

class GithubSearchTest {

    private var mockJsonString = REPO_SEARCH_RESULT
    private val mockResponse: Response = mockk(relaxUnitFun = true) {
        every { headers } returns mockk(relaxed = true) {
            every { headers.names() } returns setOf("status")
            every { headers["status"] } returns "200"
        }
        every { isSuccessful } returns true
        every { body } returns mockk(relaxUnitFun = true) {
            every { contentType() } returns mockk {
                every { charset() } returns Charsets.UTF_8
                every { source() } answers { mockJsonString.byteInputStream().source().buffer() }
            }

        }
    }
    private val mockCall: Call = mockk {
        every { execute() } returns mockResponse
    }
    private val mockOkHttpClient: OkHttpClient= mockk {
        every { newCall(any()) } returns mockCall
    }

    @Test
    fun `getReposByStars should return a list of repos with appropriate values on happy path`() {
        // arrange
        val search = GithubSearch(
            mockOkHttpClient
        )

        // act
        val result = runBlocking {
            // arbitrary string, mock client will only return a github search
             search.getReposByStars("mccartykim")
         }

        // assert
        assertThat(result)
            .isNotNull
            .hasSize(3) // matches example string from real github results

        assertThat(result)
            .extracting("name")
            .doesNotContain(null, "")

        assertThat(result)
            .extracting("html_url")
            .allMatch { url -> url is String && url.contains("https://") }

        assertThat(result.map { it.stargazers_count} )
            .isSortedAccordingTo(reverseOrder()) // descending, like in the source json, meaning order is preserved
    }

    @Test
    fun `getReposByStars should thrown an IOException if result is not successful`() {
        // arrange
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.headers["status"] } returns "500 Internal Error"
        val search = GithubSearch(
            mockOkHttpClient
        )

        // act
        val thrown = catchThrowable { runBlocking { search.getReposByStars("mccartykim") } }

        // assert
        assertThat(thrown)
            .isInstanceOf(IOException::class.java)
            .hasMessageStartingWith("500")
    }

    @Test
    fun `getReposByStars should thrown an IOException if body is null`() {
        // arrange
        every { mockResponse.body } returns null
        val search = GithubSearch(
            mockOkHttpClient
        )

        // act
        val thrown = catchThrowable { runBlocking { search.getReposByStars("mccartykim") } }

        // assert
        assertThat(thrown)
            .isInstanceOf(IOException::class.java)
    }

    @Test
    fun `getRepoByStars should return empty list with zero result response`() {
        // Arrange
        mockJsonString = EMPTY_REPO_SEARCH_RESULT
        val search = GithubSearch(mockOkHttpClient)

        // Act
        val result = runBlocking { search.getReposByStars("mccartykim") }

        //Assert
        assertThat(result)
            .isEmpty()
    }

    @Test
    fun `getMostPopularOrgs should return a list of GitHub users`() {
        val searcher = GithubSearch(mockOkHttpClient)

        val result = runBlocking { searcher.getMostPopularOrgs() }

        assertThat(result)
            .isNotEmpty
            .hasSize(1)

        assertThat(result.find { it.login == "octokit" }).isNotNull
    }

    @Test
    fun `executeRequest - Rate limiting should allow a request if no recent request`() {
        val searcher = GithubSearch(mockOkHttpClient)

        var wait = System.currentTimeMillis()
        val response = runBlocking { searcher.executeRequest(mockk()) }
        wait = System.currentTimeMillis() - wait
        assertThat(response).isNotNull
        assertThat(wait).isLessThan(100)
    }

    @Test
    fun `executeRequest - Rate limiting should allow a request if no recent request, and delay one request`() {
        val searcher = GithubSearch(mockOkHttpClient)
        runBlocking { searcher.executeRequest(mockk()) }
        val wait = measureTimeMillis { val response = runBlocking { searcher.executeRequest(mockk()) } }

        assertThat(wait).isGreaterThan(1500)
    }

    @Test
    fun `executeRequest - Rate limiting should allow a request if no recent request, delay one request, and immediately return null if waiting on a previous request`() {
        val searcher = GithubSearch(mockOkHttpClient)
        GlobalScope.launch { searcher.executeRequest(mockk()) }
        Thread.sleep(100)
        GlobalScope.launch { searcher.executeRequest(mockk()) }
        Thread.sleep(100)
        var response: Response? = null
        val wait = measureTimeMillis { response = runBlocking { searcher.executeRequest(mockk())  } }

        assertThat(wait).isLessThan(100)
        assertThat(response).isNull()
    }
}
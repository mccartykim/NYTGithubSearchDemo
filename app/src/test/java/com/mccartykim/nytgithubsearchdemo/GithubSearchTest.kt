package com.mccartykim.nytgithubsearchdemo

import com.mccartykim.nytgithubsearchdemo.search.GithubSearch
import io.mockk.every
import io.mockk.mockk
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

class GithubSearchTest {

    private var mockJsonString = REPO_SEARCH_RESULT
    private val mockResponse: Response = mockk{
        every { headers } returns mockk(relaxed = true)
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
            .isNotNull()
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
        val search = GithubSearch(
            mockOkHttpClient
        )

        // act
        val thrown = catchThrowable { runBlocking { search.getReposByStars("mccartykim") } }

        // assert
        assertThat(thrown)
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("Network Error: ")
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
        mockJsonString = EMPTY_REPO_SEARCH_RESULT
        val search = GithubSearch(mockOkHttpClient)

        val result = runBlocking { search.getReposByStars("mccartykim") }
        assertThat(result)
            .isEmpty()
    }

}
package com.mccartykim.nytgithubsearchdemo

import com.mccartykim.nytgithubsearchdemo.search.*
import io.mockk.*
import io.mockk.impl.annotations.SpyK
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class GithubSearchViewModelTest {
    private var mockReposByStars: List<RepoListing> = emptyList()
    private var mockMostPopularOrgs: List<GithubUser> = listOf(
        GithubUser("github", UserType.Organization), GithubUser("google", UserType.Organization)
    )
    private val mockSearcher:GithubSearch = mockk() {
        coEvery { getReposByStars(any(), any()) } answers { mockReposByStars }
        coEvery { getMostPopularOrgs(any()) } answers { mockMostPopularOrgs }
    }
    private val nopConsumer: (ViewModelEvents) -> Unit = spyk({ _: ViewModelEvents -> })
    private val viewModel = spyk(GithubSearchViewModel())

    @Before
    fun before() {
        // On a larger project, I'd use dagger to inject the mockk here
        viewModel.searcher = mockSearcher
        viewModel.viewModelSubject.subscribe{ nopConsumer(it) }

    }

    @Test
    fun `On initially starting, all fields are blank, and submit is disabled`() {
        // Assert
        assertThat(viewModel.query).isBlank()
        assertThat(viewModel.searchResults).isEmpty()
        assertThat(viewModel.submitEnabled).isFalse()
        verify { nopConsumer wasNot called }
    }

    @Test
    fun `On inputting a valid query, enable the submit button`() {
        // Act
        viewModel.query = "test"

        assertThat(viewModel.query).isEqualTo("test")
        assertThat(viewModel.searchResults).isEmpty()
        assertThat(viewModel.submitEnabled).isTrue()
        verify { nopConsumer wasNot called }
    }

    @Test
    fun `On inputting an invalid query, disable the submit button`() {
        // Act
        viewModel.query = "-test"

        assertThat(viewModel.query).isEqualTo("-test")
        assertThat(viewModel.searchResults).isEmpty()
        assertThat(viewModel.submitEnabled).isFalse()
        verify { nopConsumer wasNot called }
    }

    @Test
    fun `On inputting an invalid query and sending submit (say via the keyboard) flash a warning toast`() {
        // Arrange
        viewModel.query = "-test"
        // Act
        viewModel.submit()

        assertThat(viewModel.query).isEqualTo("-test")
        assertThat(viewModel.searchResults).isEmpty()
        assertThat(viewModel.submitEnabled).isFalse()
        verify { nopConsumer(any<Warning>()) }
    }

    @Test
    fun `On inputting a valid query and sending submit and recieving results, view search results`() {
        mockReposByStars = listOf(
            RepoListing(
                "fakerepo", "This is fake", 200, "https://example.net",
                owner = GithubUser("fake", UserType.Organization)
            ))
        // Arrange
        viewModel.query = "github"
        // Act
        viewModel.submit()

        // wait for and verify submit coroutine has finished
        coVerify { viewModel.submit("github") }
        assertThat(viewModel.query).isEqualTo("")
        assertThat(viewModel.searchResults)
            .hasSize(1)
            .first()
                .isEqualToComparingFieldByField(mockReposByStars.first())
        assertThat(viewModel.submitEnabled).isFalse()
    }

    @Test
    fun `On inputting a valid query and sending submit and receiving results, send event to launch custom tab after onClick`() {
        // Arrange
        mockReposByStars = listOf(
            RepoListing(
                "fakerepo", "This is fake", 200, "https://example.net",
                owner = GithubUser("fake", UserType.Organization)
            ))
        viewModel.query = "github"
        // Act

        viewModel.submit()

        // wait for and verify submit coroutine has finished
        coVerify { viewModel.submit("github") }

        // click first item
        viewModel.resultItemClicked(0)

        val events: MutableList<ViewModelEvents> = mutableListOf()
        verify { nopConsumer(capture(events)) }
        assertThat(events.any { it is LoadGithubPage })
        assertThat((events.find{ it is LoadGithubPage } as LoadGithubPage).url).isEqualTo("https://example.net")
    }

    @Test
    fun `On inputting a valid query and sending submit and receiving results, send message to preload top result`() {
        // Arrange
        mockReposByStars = listOf(
            RepoListing(
                "fakerepo", "This is fake", 200, "https://example.net",
                owner = GithubUser("fake", UserType.Organization)
            ))
        viewModel.query = "github"
        // Act

        viewModel.submit()

        // wait for and verify submit coroutine has finished
        coVerify { viewModel.submit("github") }

        // click first item
        viewModel.resultItemClicked(0)

        val events: MutableList<ViewModelEvents> = mutableListOf()
        verify { nopConsumer(capture(events)) }
        assertThat(events.any { it is PreloadTopLink })
        assertThat((events.find{ it is PreloadTopLink } as PreloadTopLink).url).isEqualTo("https://example.net")
    }

    @Test
    fun `On inputting a valid query and getting a network error exception, get network error listing that does not send event to launch custom tab after onClick`() {
        // Arrange
        viewModel.query = "badrequest"
        coEvery { mockSearcher.getReposByStars(any()) } throws IOException("500 internal error")

        // Act
        viewModel.submit()

        // wait for and verify submit coroutine has finished
        coVerify { viewModel.submit("badrequest") }

        // wait for updates to propagate
        Thread.sleep(100)
        assertThat(viewModel.searchResults)
            .hasSize(1)
            .first()
                .isInstanceOf(ErrorListing::class.java)

        // click first item
        viewModel.resultItemClicked(0)
        verify(exactly = 0) { nopConsumer(any<LoadGithubPage>()) }
    }

    @Test
    fun `On inputting a valid query and getting no results, get "No Results" listing that does not send event to launch custom tab after onClick`() {
        // Arrange
        viewModel.query = "emptyrequest"
        coEvery { mockSearcher.getReposByStars(any()) } coAnswers { emptyList() }

        // Act
        viewModel.submit()

        // wait for and verify submit coroutine has finished
        coVerify { viewModel.submit("emptyrequest") }

        // wait for updates to propagate
        Thread.sleep(100)
        assertThat(viewModel.searchResults)
            .hasSize(1)
            .first()
            .isInstanceOf(EmptyListing::class.java)

        // click first item
        viewModel.resultItemClicked(0)
        verify(exactly = 0) { nopConsumer(any<LoadGithubPage>()) }
    }

    @Test
    fun `Loading bar should be displayed on sending a query`() {
        mockReposByStars = listOf(
            RepoListing(
                "fakerepo", "This is fake", 200, "https://example.net",
                owner = GithubUser("fake", UserType.Organization)
            ))
        var boolSpy = spyk(false)
        // Arrange
        viewModel.query = "github"
        // Act
        viewModel.submit()

        // wait for and verify submit coroutine has finished
        coVerify { viewModel.submit("github") }
        verify { viewModel.isLoading = true }
        verify { viewModel.isLoading = false }
    }

    @Test
    fun `suggestedOrgs is empty on initially starting`() {
        assertThat(viewModel.suggestedOrgs).isEmpty()
    }

    @Test
    fun `suggestedOrgs is populated after loadSuggestions is called initially starting`() {
        assertThat(viewModel.suggestedOrgs).isEmpty()
        runBlocking { viewModel.loadSuggestions() }
        assertThat(viewModel.suggestedOrgs).containsAll(mockMostPopularOrgs.map { it.login })
    }
}
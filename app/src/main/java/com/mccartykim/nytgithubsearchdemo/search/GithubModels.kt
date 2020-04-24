package com.mccartykim.nytgithubsearchdemo.search

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class GithubUser(val login: String, val avatar_url: String, val type: UserType)

enum class UserType(val type: String) {
    Organization("Organization"),
    User("User")
}

open class Listing(val name: String, val description: String?, val isError: Boolean = false)

object ErrorListing: Listing("Network Error", "Please try again")

@JsonClass(generateAdapter = true)
class RepoListing(name: String, val html_url: String, val stargazers_count: Int, description: String?, val owner: GithubUser): Listing(name, description)

@JsonClass(generateAdapter = true)
class RepoSearchResults(val items: List<RepoListing>)

@JsonClass(generateAdapter = true)
class UserSearchResults(val items: List<GithubUser>)

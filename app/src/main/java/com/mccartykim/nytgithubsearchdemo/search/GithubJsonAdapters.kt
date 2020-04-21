package com.mccartykim.nytgithubsearchdemo.search

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class GithubUser(val login: String, val avatar_url: String)

@JsonClass(generateAdapter = true)
class RepoListing(val name: String, val html_url: String, val stargazers_count: Int, val description: String?, val owner: GithubUser)

@JsonClass(generateAdapter = true)
class RepoSearchResults(val items: List<RepoListing>)
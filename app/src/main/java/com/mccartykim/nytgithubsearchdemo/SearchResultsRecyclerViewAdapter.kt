package com.mccartykim.nytgithubsearchdemo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.mccartykim.nytgithubsearchdemo.search.Listing
import com.mccartykim.nytgithubsearchdemo.search.RepoListing

class SearchResultsRecyclerViewAdapter(private val viewModel: GithubSearchViewModel) : RecyclerView.Adapter<SearchResultViewHolder>() {

    /*
        With a dataset that partially changes, I would not replace the entire list and call "notifyDataSetChanged."
        However, at just three items that will likely be different every time, this isn't wasteful.
        I mostly chose to use this instead of a ListView because I wanted to demo recyclerviews
     */
    var dataSet: List<Listing> = viewModel.searchResults
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val searchResultView: ViewGroup = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_result_item, parent, false) as ViewGroup
        return SearchResultViewHolder(searchResultView, viewModel)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.updateFromRepoListing(dataSet[position])
    }

    override fun getItemCount(): Int = dataSet.size
}

class SearchResultViewHolder(private val resultGroup: ViewGroup, private val viewModel: GithubSearchViewModel): RecyclerView.ViewHolder(resultGroup) {
    private val repoTitle = resultGroup.findViewById<TextView>(R.id.listing_title)
    private val repoDescription = resultGroup.findViewById<TextView>(R.id.listing_description)
    private val repoStars = resultGroup.findViewById<TextView>(R.id.repo_star_count)
    private val cardView = resultGroup.findViewById<CardView>(R.id.result_card)

    fun updateFromRepoListing(listing: Listing) {
        when (listing) {
            is RepoListing -> {
                repoStars.visibility = View.VISIBLE
                repoStars.text =
                    resultGroup.resources.getString(R.string.star_count, listing.stargazers_count)
            }
            else -> {
                repoStars.visibility = View.GONE
            }
        }
        when {
            listing.description.isNullOrBlank() -> repoDescription.visibility = View.INVISIBLE
            else -> {
                repoDescription.visibility =  View.VISIBLE
                repoDescription.text = listing.description
            }
        }
        repoTitle.text = listing.name
        cardView.setOnClickListener { viewModel.resultItemClicked(adapterPosition) }
    }
}


package com.mccartykim.nytgithubsearchdemo

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mccartykim.nytgithubsearchdemo.search.Listing
import com.mccartykim.nytgithubsearchdemo.search.RepoListing

class SearchResultsAdapter : RecyclerView.Adapter<SearchResultViewHolder>() {
    var dataSet: List<Listing> = listOf()


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val searchResultView: ViewGroup = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_result_item, parent, false) as ViewGroup
        return SearchResultViewHolder(searchResultView)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.updateFromRepoListing(dataSet[position])
    }

    override fun getItemCount(): Int = dataSet.size
}

class SearchResultViewHolder(private val resultGroup: ViewGroup): RecyclerView.ViewHolder(resultGroup) {
    private val repoTitle = resultGroup.findViewById<TextView>(R.id.repo_title)
    private val repoDescription = resultGroup.findViewById<TextView>(R.id.repo_description)
    private val repoStars = resultGroup.findViewById<TextView>(R.id.repo_star_count)

    // TODO MVVMify this
    fun updateFromRepoListing(listing: Listing) {
        // TODO consider adding icon, perhaps with picasso?
        repoTitle.text = listing.name
        Log.d("SearchResultViewholder", "updating")
        // Probably shouldn't do that in the view layer vvv
        when {
            listing.description.isNullOrBlank() -> repoDescription.visibility = View.INVISIBLE
            else -> {
                repoDescription.visibility =  View.VISIBLE
                repoDescription.text = listing.description
            }
        }
        when (listing) {
            is RepoListing -> {
                repoStars.visibility = View.VISIBLE
                repoStars.text =
                    resultGroup.resources.getString(R.string.star_count, listing.stargazers_count)
                resultGroup.setOnClickListener { MainViewModel.viewSubject.onNext(ListingClicked(layoutPosition)) }
            }
            else -> {
                repoStars.visibility = View.GONE
            }
        }
    }
}

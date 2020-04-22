package com.mccartykim.nytgithubsearchdemo

import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.recyclerview.widget.RecyclerView
import com.mccartykim.nytgithubsearchdemo.search.RepoListing

class SearchResultsAdapter(private val dataSet: List<RepoListing>) : RecyclerView.Adapter<SearchResultViewHolder>() {
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
    fun updateFromRepoListing(repoListing: RepoListing) {
        // TODO consider adding icon, perhaps with picasso?
        repoTitle.text = repoListing.name
        Log.d("SearchResultViewholder", "updating")
        // Probably shouldn't do that in the view layer vvv
        repoDescription.text = repoListing.description
        when {
            repoListing.description.isNullOrBlank() -> repoDescription.visibility = View.INVISIBLE
            else -> repoDescription.visibility = View.VISIBLE
        }
        // yuck
        repoStars.text = resultGroup.resources.getString(R.string.star_count, repoListing.stargazers_count)

        resultGroup.setOnClickListener {
            CustomTabsIntent.Builder()
                .setToolbarColor(Color.rgb(201, 66, 128))
                .setShowTitle(true)
                .setStartAnimations(resultGroup.context, android.R.anim.slide_out_right, android.R.anim.slide_in_left)
                .setExitAnimations(resultGroup.context, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .build()
                .launchUrl(resultGroup.context, Uri.parse(repoListing.html_url))
        }
    }
}
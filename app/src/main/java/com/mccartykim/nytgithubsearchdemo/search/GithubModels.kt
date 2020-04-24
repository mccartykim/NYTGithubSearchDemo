package com.mccartykim.nytgithubsearchdemo.search

import android.os.Parcel
import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
class GithubUser(val login: String, val type: UserType): Parcelable

enum class UserType(val type: String) {
    Organization("Organization"),
    User("User")
}

@Parcelize
open class Listing(val name: String, val description: String?): Parcelable

@Parcelize
object ErrorListing: Listing("Network Error", "Please try again")
@Parcelize
object EmptyListing: Listing("No Public Results", "Either the results do not exist or are private")

@JsonClass(generateAdapter = true)
class RepoListing constructor(
    name: String,
    description: String?,
    val stargazers_count: Int,
    val html_url: String,
    val owner: GithubUser
): Listing(name, description) {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString(),
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readParcelable(GithubUser::class.java.classLoader)!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        super.writeToParcel(parcel, flags)
        parcel.writeInt(stargazers_count)
        parcel.writeString(html_url)
        parcel.writeParcelable(owner, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RepoListing> {
        override fun createFromParcel(parcel: Parcel): RepoListing {
            return RepoListing(parcel)
        }

        override fun newArray(size: Int): Array<RepoListing?> {
            return arrayOfNulls(size)
        }
    }
}

@JsonClass(generateAdapter = true)
class RepoSearchResults(val items: List<RepoListing>)

@JsonClass(generateAdapter = true)
class UserSearchResults(val items: List<GithubUser>)

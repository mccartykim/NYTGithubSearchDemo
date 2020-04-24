package com.mccartykim.nytgithubsearchdemo

import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mccartykim.nytgithubsearchdemo.search.Listing

@BindingAdapter("autocompleteAdapterList", "autocompleteAdapterResource")
fun setAutocompleteAdapter(view: AutoCompleteTextView, list: List<String>, resId: Int) {
    Log.d("BIND", "set autocomplete list")
    view.setAdapter(ArrayAdapter(view.context, resId, list))
}

@BindingAdapter("onDone")
fun setOnDone(view: EditText, onDone: () -> Unit) {
    view.setOnEditorActionListener { _, actionId, _ ->
        when (actionId) {
            EditorInfo.IME_ACTION_DONE -> { onDone(); true}
            else -> false
        }
    }
}

@BindingAdapter("android:onClick")
fun onClick(view: Button, onClick: () -> Unit) {
    view.setOnClickListener { _ -> onClick() }
}

@BindingAdapter("onItemClicked")
fun setOnItemClicked(v: AutoCompleteTextView, onItemClicked: () -> Unit) {
    v.onItemClickListener = AdapterView.OnItemClickListener {
            _, _, _, _ -> onItemClicked()
    }
}

// Note: I would hesitate to use this specific a binding in a published piece of software, at least without making it more generic.
// I mostly just wanted to stick with the general theme of using bindings to pass data to and from view components in this app
@BindingAdapter("data_set")
fun setData(view: RecyclerView, data: List<Listing>){
    with (view.adapter) {
        if (this is SearchResultsRecyclerViewAdapter) {
            dataSet = data
        }
    }
}

package com.sample.protifydroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClientListViewAdapter(private val activity: MainActivity, var dataSet: List<Client>) :
    RecyclerView.Adapter<ClientListViewAdapter.ViewHolder>() {
    class ViewHolder(private val activity: MainActivity, view: View) : RecyclerView.ViewHolder(view) {
        /**
         * Provide a reference to the type of views that you are using
         * (custom ViewHolder).
         */
        val textView: TextView = view.findViewById(R.id.textViewRowItem)
        init {
            // Define click listener for the ViewHolder's View.
            textView.setOnClickListener {
                activity.onClientClicked(adapterPosition)
            }
        }
    }
    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.text_view_row_item, viewGroup, false)
        return ViewHolder(activity, view)
    }
    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.textView.text = dataSet[position].name
    }
    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

}
package co.terminal.radio

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.terminal.radio.databinding.ItemStationBinding

class StationAdapter(
    private val onItemClick: (Station) -> Unit
) : ListAdapter<Station, StationAdapter.ViewHolder>(StationDiffCallback()) {

    class ViewHolder(
        private val binding: ItemStationBinding,
        private val onItemClick: (Station) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(station: Station) {
            binding.tvStationName.text = station.name
            binding.root.setOnClickListener { onItemClick(station) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StationDiffCallback : DiffUtil.ItemCallback<Station>() {
        override fun areItemsTheSame(oldItem: Station, newItem: Station) =
            oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: Station, newItem: Station) =
            oldItem == newItem
    }
}

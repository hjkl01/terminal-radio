package co.terminal.radio

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.terminal.radio.databinding.ItemStationBinding

class StationAdapter(
    private val onItemClick: (Station) -> Unit
) : ListAdapter<Station, StationAdapter.ViewHolder>(StationDiffCallback()) {

    var currentPlayingUrl: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(
        private val binding: ItemStationBinding,
        private val onItemClick: (Station) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(station: Station, isPlaying: Boolean, onPlayClick: () -> Unit) {
            binding.tvStationName.text = station.name
            binding.ivPlayingIcon.visibility = if (isPlaying) android.view.View.VISIBLE else android.view.View.GONE
            
            if (isPlaying) {
                binding.tvStationName.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.holo_green_dark))
            } else {
                binding.tvStationName.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.primary_text_light_nodisable))
            }
            
            binding.root.setOnClickListener { 
                onItemClick(station)
                onPlayClick()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = getItem(position)
        holder.bind(station, station.url == currentPlayingUrl, {})
    }

    class StationDiffCallback : DiffUtil.ItemCallback<Station>() {
        override fun areItemsTheSame(oldItem: Station, newItem: Station) =
            oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: Station, newItem: Station) =
            oldItem == newItem
    }
}

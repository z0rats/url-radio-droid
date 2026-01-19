package com.urlradiodroid.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.databinding.ItemStationBinding
import com.urlradiodroid.util.EmojiGenerator

class StationAdapter(
    private val onStationClick: (RadioStation) -> Unit,
    private val onStationLongClick: (RadioStation) -> Unit,
    private val onStationEdit: (RadioStation) -> Unit,
    private val onStationDelete: (RadioStation) -> Unit
) : ListAdapter<RadioStation, StationAdapter.StationViewHolder>(StationDiffCallback()) {

    var swipedViewHolder: StationViewHolder? = null

    fun resetAllSwipes() {
        swipedViewHolder?.resetSwipePosition()
        swipedViewHolder = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val binding = ItemStationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StationViewHolder(binding, this)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StationViewHolder(
        private val binding: ItemStationBinding,
        private val adapter: StationAdapter
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(station: RadioStation) {
            binding.textViewStationName.text = station.name
            binding.textViewStationEmoji.text = EmojiGenerator.getEmojiForStation(station.name, station.streamUrl)
            
            // Always show URL under station name
            binding.textViewStationUrl.text = station.streamUrl

            binding.cardStation.setOnClickListener {
                // Always open station on click - single click opens immediately
                // If item is swiped, reset swipe first (but still open)
                if (adapter.swipedViewHolder == this) {
                    resetSwipePosition()
                }
                onStationClick(station)
            }

            binding.cardStation.setOnLongClickListener {
                // Long press opens edit (settings)
                // If item is swiped, reset swipe first
                if (adapter.swipedViewHolder == this) {
                    resetSwipePosition()
                }
                // Always open edit on long press
                onStationEdit(station)
                true
            }

            binding.buttonEdit.setOnClickListener {
                resetSwipePosition()
                onStationEdit(station)
            }

            binding.buttonDelete.setOnClickListener {
                resetSwipePosition()
                onStationDelete(station)
            }

            // Reset swipe position on bind
            resetSwipePosition()
        }

        fun resetSwipePosition() {
            binding.cardStation.translationX = 0f
            binding.layoutSwipeButtons.visibility = View.GONE
            if (adapter.swipedViewHolder == this) {
                adapter.swipedViewHolder = null
            }
        }

        fun onSwipe(dX: Float) {
            if (dX < 0) {
                // Swiping left - show buttons
                // Reset previous swiped item
                if (adapter.swipedViewHolder != null && adapter.swipedViewHolder != this) {
                    adapter.swipedViewHolder?.resetSwipePosition()
                }
                adapter.swipedViewHolder = this
                
                // Limit swipe to button width - don't allow swiping beyond buttons
                val maxSwipe = if (binding.layoutSwipeButtons.width > 0) {
                    -binding.layoutSwipeButtons.width.toFloat()
                } else {
                    // If width not measured yet, use a reasonable default
                    -200f
                }
                val limitedDx = dX.coerceAtLeast(maxSwipe)
                binding.cardStation.translationX = limitedDx
                binding.layoutSwipeButtons.visibility = View.VISIBLE
            } else if (dX == 0f) {
                // Swipe released - keep buttons visible if swiped enough
                val swipeThreshold = if (binding.layoutSwipeButtons.width > 0) {
                    -binding.layoutSwipeButtons.width.toFloat()
                } else {
                    -200f
                }
                if (binding.cardStation.translationX < swipeThreshold / 2) {
                    // Keep buttons visible
                    binding.cardStation.translationX = swipeThreshold
                } else {
                    // Hide buttons
                    resetSwipePosition()
                }
            }
        }
    }

    private class StationDiffCallback : DiffUtil.ItemCallback<RadioStation>() {
        override fun areItemsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean {
            return oldItem == newItem
        }
    }
}

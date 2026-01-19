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

            binding.cardStation.setOnClickListener {
                // Reset all swipes before opening
                adapter.resetAllSwipes()
                onStationClick(station)
            }
            binding.cardStation.setOnLongClickListener {
                // Reset all swipes before opening
                adapter.resetAllSwipes()
                onStationLongClick(station)
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
                binding.cardStation.translationX = dX
                binding.layoutSwipeButtons.visibility = View.VISIBLE
            } else if (dX == 0f) {
                // Swipe released - keep buttons visible if swiped enough
                val swipeThreshold = -binding.layoutSwipeButtons.width.toFloat()
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

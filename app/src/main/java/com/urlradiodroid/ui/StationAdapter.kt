package com.urlradiodroid.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chauthai.swipereveallayout.SwipeRevealLayout
import com.chauthai.swipereveallayout.ViewBinderHelper
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.databinding.ItemStationBinding
import com.urlradiodroid.util.EmojiGenerator

class StationAdapter(
    private val onStationClick: (RadioStation) -> Unit,
    private val onStationLongClick: (RadioStation) -> Unit,
    private val onStationEdit: (RadioStation) -> Unit,
    private val onStationDelete: (RadioStation) -> Unit
) : ListAdapter<RadioStation, StationAdapter.StationViewHolder>(StationDiffCallback()) {

    private val viewBinderHelper = ViewBinderHelper().apply {
        setOpenOnlyOne(true) // Only one item can be swiped open at a time
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
        val station = getItem(position)
        viewBinderHelper.bind(holder.binding.swipeRevealLayout, station.id.toString())
        holder.bind(station)
    }

    inner class StationViewHolder(
        val binding: ItemStationBinding,
        private val adapter: StationAdapter
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(station: RadioStation) {
            binding.textViewStationName.text = station.name
            // Use custom icon if available, otherwise generate emoji
            binding.textViewStationEmoji.text = station.customIcon
                ?: EmojiGenerator.getEmojiForStation(station.name, station.streamUrl)

            // Always show URL under station name
            binding.textViewStationUrl.text = station.streamUrl

            // Click listener for card - open station
            binding.cardStation.setOnClickListener {
                // Close swipe if open
                if (binding.swipeRevealLayout.isOpened) {
                    binding.swipeRevealLayout.close(true)
                } else {
                    onStationClick(station)
                }
            }

            // Long press for edit with progressive animation
            val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout() + 300
            var longPressRunnable: Runnable? = null
            var progressRunnable: Runnable? = null
            var initialX = 0f
            var initialY = 0f
            var isSwipeStarted = false
            var holdStartTime = 0L
            val animationDuration = longPressTimeout.toLong()

            fun resetAnimation() {
                binding.cardStation.animate().cancel()
                binding.cardStation.scaleX = 1f
                binding.cardStation.scaleY = 1f
                binding.cardStation.alpha = 1f
                binding.cardStation.translationZ = 0f
            }

            fun updateProgressAnimation(elapsed: Long) {
                if (isSwipeStarted || binding.swipeRevealLayout.isOpened) {
                    resetAnimation()
                    return
                }

                val progress = (elapsed.toFloat() / animationDuration).coerceIn(0f, 1f)

                // Progressive scale: from 1.0 to 0.96 (slight shrink for feedback)
                val scale = 1f - (progress * 0.04f)
                binding.cardStation.scaleX = scale
                binding.cardStation.scaleY = scale

                // Progressive alpha: from 1.0 to 0.88 (subtle fade)
                binding.cardStation.alpha = 1f - (progress * 0.12f)

                // Progressive translationZ: lift effect (0 to 12dp)
                val maxLift = 12f * binding.root.context.resources.displayMetrics.density
                binding.cardStation.translationZ = progress * maxLift

                // Continue animation if not completed
                if (progress < 1f && !isSwipeStarted) {
                    progressRunnable = Runnable {
                        val currentElapsed = System.currentTimeMillis() - holdStartTime
                        updateProgressAnimation(currentElapsed)
                    }
                    binding.cardStation.postDelayed(progressRunnable!!, 16) // ~60fps
                }
            }

            binding.cardStation.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = event.x
                        initialY = event.y
                        isSwipeStarted = false
                        holdStartTime = System.currentTimeMillis()

                        // Don't start long press if item is swiped
                        if (!binding.swipeRevealLayout.isOpened) {
                            // Start progressive animation
                            updateProgressAnimation(0)

                            longPressRunnable = Runnable {
                                if (!binding.swipeRevealLayout.isOpened && !isSwipeStarted) {
                                    // Trigger haptic feedback on long press activation
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    onStationEdit(station)
                                    resetAnimation()
                                }
                            }
                            view.postDelayed(longPressRunnable!!, longPressTimeout.toLong())
                        }
                        false // Don't consume - allow SwipeRevealLayout to handle
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        // Check if user is swiping (horizontal movement)
                        val deltaX = kotlin.math.abs(event.x - initialX)
                        val deltaY = kotlin.math.abs(event.y - initialY)
                        // If horizontal movement is significant, cancel long press
                        if (deltaX > 20 || deltaY > 20) {
                            isSwipeStarted = true
                            longPressRunnable?.let { view.removeCallbacks(it) }
                            longPressRunnable = null
                            progressRunnable?.let { binding.cardStation.removeCallbacks(it) }
                            progressRunnable = null
                            resetAnimation()
                        } else {
                            // Update animation based on elapsed time
                            val elapsed = System.currentTimeMillis() - holdStartTime
                            updateProgressAnimation(elapsed)
                        }
                        false
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { view.removeCallbacks(it) }
                        longPressRunnable = null
                        progressRunnable?.let { binding.cardStation.removeCallbacks(it) }
                        progressRunnable = null
                        isSwipeStarted = false

                        // Animate back to normal state
                        binding.cardStation.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .translationZ(0f)
                            .setDuration(150)
                            .start()
                        false
                    }
                    else -> false
                }
            }

            // Button click listeners
            binding.buttonEdit.setOnClickListener {
                binding.swipeRevealLayout.close(true)
                onStationEdit(station)
            }

            binding.buttonDelete.setOnClickListener {
                binding.swipeRevealLayout.close(true)
                onStationDelete(station)
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

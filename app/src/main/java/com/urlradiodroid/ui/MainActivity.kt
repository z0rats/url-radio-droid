package com.urlradiodroid.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urlradiodroid.R
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StationAdapter
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        database = AppDatabase.getDatabase(this)

        adapter = StationAdapter(
            onStationClick = { station ->
                val intent = Intent(this, PlaybackActivity::class.java).apply {
                    putExtra(PlaybackActivity.EXTRA_STATION_ID, station.id)
                    putExtra(PlaybackActivity.EXTRA_STATION_NAME, station.name)
                    putExtra(PlaybackActivity.EXTRA_STREAM_URL, station.streamUrl)
                }
                startActivity(intent)
            },
            onStationLongClick = { station ->
                val intent = Intent(this, AddStationActivity::class.java).apply {
                    putExtra(AddStationActivity.EXTRA_STATION_ID, station.id)
                }
                startActivity(intent)
            },
            onStationEdit = { station ->
                val intent = Intent(this, AddStationActivity::class.java).apply {
                    putExtra(AddStationActivity.EXTRA_STATION_ID, station.id)
                }
                startActivity(intent)
            },
            onStationDelete = { station ->
                showDeleteConfirmation(station)
            }
        )

        binding.recyclerViewStations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewStations.adapter = adapter

        setupSwipeToEdit()

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddStationActivity::class.java))
        }

        loadStations()
    }

    private fun setupWindowInsets() {
        // Apply insets to AppBarLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Apply insets to RecyclerView - add bottom padding for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerViewStations) { v, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                navigationBars.bottom + v.paddingBottom
            )
            insets
        }

        // Apply insets to FAB - position above navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAdd) { v, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val layoutParams = v.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            layoutParams?.bottomMargin = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.fab_margin)
            v.requestLayout()
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        loadStations()
    }

    private fun loadStations() {
        lifecycleScope.launch {
            val stations = database.radioStationDao().getAllStations()
            adapter.submitList(stations)
            binding.textViewEmpty.visibility = if (stations.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }

    private fun setupSwipeToEdit() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT
        ) {
            private var swipedPosition = RecyclerView.NO_POSITION
            private var swipedViewHolder: StationAdapter.StationViewHolder? = null

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Don't actually remove item, just show buttons
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val stationViewHolder = viewHolder as? StationAdapter.StationViewHolder
                    if (stationViewHolder != null) {
                        stationViewHolder.onSwipe(dX)
                    }
                } else {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val stationViewHolder = viewHolder as? StationAdapter.StationViewHolder
                if (stationViewHolder != null) {
                    // Check if swipe was enough to show buttons
                    val swipeButtonsLayout = stationViewHolder.itemView.findViewById<View>(R.id.layoutSwipeButtons)
                    val cardStation = stationViewHolder.itemView.findViewById<View>(R.id.cardStation)

                    if (swipeButtonsLayout.width > 0) {
                        val swipeButtonsWidth = swipeButtonsLayout.width
                        val currentTranslation = cardStation.translationX
                        if (currentTranslation < -swipeButtonsWidth / 2) {
                            // Keep buttons visible - animate to full swipe
                            cardStation.animate()
                                .translationX(-swipeButtonsWidth.toFloat())
                                .setDuration(200)
                                .start()
                            swipeButtonsLayout.visibility = View.VISIBLE
                        } else {
                            // Hide buttons - animate back
                            cardStation.animate()
                                .translationX(0f)
                                .setDuration(200)
                                .start()
                            swipeButtonsLayout.visibility = View.GONE
                        }
                    } else {
                        // Layout not measured yet, use onSwipe with 0 to handle it
                        stationViewHolder.onSwipe(0f)
                    }
                }
            }

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                return ItemTouchHelper.LEFT
            }
        })

        itemTouchHelper.attachToRecyclerView(binding.recyclerViewStations)
    }

    private fun showDeleteConfirmation(station: com.urlradiodroid.data.RadioStation) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_station))
            .setMessage(getString(R.string.delete_station_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteStation(station)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteStation(station: com.urlradiodroid.data.RadioStation) {
        lifecycleScope.launch {
            try {
                database.radioStationDao().deleteStation(station.id)
                Toast.makeText(this@MainActivity, getString(R.string.station_deleted), Toast.LENGTH_SHORT).show()
                loadStations()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

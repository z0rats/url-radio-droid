package com.urlradiodroid.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urlradiodroid.R
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StationAdapter
    private lateinit var database: AppDatabase
    private var allStations: List<RadioStation> = emptyList()
    private var filteredStations: List<RadioStation> = emptyList()

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
                // Long press opens edit (settings) - handled in adapter now
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

        // Optimize RecyclerView for large lists
        binding.recyclerViewStations.setHasFixedSize(true)
        binding.recyclerViewStations.setItemViewCacheSize(20)
        binding.recyclerViewStations.recycledViewPool.setMaxRecycledViews(0, 20)

        setupSearch()
        setupScrollListener()

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddStationActivity::class.java))
        }

        loadStations()
    }

    private fun setupSearch() {
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterStations(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupScrollListener() {
        binding.recyclerViewStations.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // If scrolling up (dy < 0) and search has focus, clear focus
                if (dy < 0 && binding.editTextSearch.hasFocus()) {
                    binding.editTextSearch.clearFocus()
                    // Hide keyboard
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(binding.editTextSearch.windowToken, 0)
                }
            }
        })
    }

    private fun filterStations(query: String) {
        val queryLower = query.lowercase().trim()
        filteredStations = if (queryLower.isEmpty()) {
            allStations
        } else {
            allStations.filter { station ->
                // Search by name (partial match) OR full URL (exact match)
                station.name.lowercase().contains(queryLower) ||
                station.streamUrl.lowercase() == queryLower
            }
        }
        adapter.submitList(filteredStations)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = filteredStations.isEmpty()
        val hasSearchQuery = binding.editTextSearch.text?.toString()?.isNotBlank() == true
        binding.textViewEmpty.visibility = if (isEmpty && !hasSearchQuery) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun setupWindowInsets() {
        // Apply insets to AppBarLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Apply insets to search input if visible
        ViewCompat.setOnApplyWindowInsetsListener(binding.textInputLayoutSearch) { v, insets ->
            // Search bar already has proper margins, no additional insets needed
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
            allStations = database.radioStationDao().getAllStations()

            // Show search bar only if there are more than 4 stations
            val shouldShowSearch = allStations.size > 4
            binding.textInputLayoutSearch.visibility = if (shouldShowSearch) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // Apply current search filter or show all stations
            val currentQuery = binding.editTextSearch.text?.toString() ?: ""
            if (currentQuery.isBlank()) {
                filteredStations = allStations
                adapter.submitList(allStations)
            } else {
                filterStations(currentQuery)
            }

            updateEmptyState()
        }
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
                // Clear search when deleting to show updated list
                binding.editTextSearch.setText("")
                loadStations()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

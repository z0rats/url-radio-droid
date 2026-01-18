package com.urlradiodroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StationAdapter
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)

        adapter = StationAdapter { station ->
            val intent = Intent(this, PlaybackActivity::class.java).apply {
                putExtra(PlaybackActivity.EXTRA_STATION_ID, station.id)
                putExtra(PlaybackActivity.EXTRA_STATION_NAME, station.name)
                putExtra(PlaybackActivity.EXTRA_STREAM_URL, station.streamUrl)
            }
            startActivity(intent)
        }

        binding.recyclerViewStations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewStations.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddStationActivity::class.java))
        }

        loadStations()
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
}

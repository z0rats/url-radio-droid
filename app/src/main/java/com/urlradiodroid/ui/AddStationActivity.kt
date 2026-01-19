package com.urlradiodroid.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.urlradiodroid.R
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.databinding.ActivityAddStationBinding
import kotlinx.coroutines.launch
import java.net.URL

class AddStationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddStationBinding
    private lateinit var database: AppDatabase
    private var editingStationId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAddStationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        database = AppDatabase.getDatabase(this)

        editingStationId = intent.getLongExtra(EXTRA_STATION_ID, -1L).takeIf { it != -1L }

        if (editingStationId != null) {
            binding.toolbarAddStation.title = getString(R.string.edit_station)
            loadStationForEditing()
        } else {
            binding.toolbarAddStation.title = getString(R.string.add_station)
        }

        binding.toolbarAddStation.setNavigationOnClickListener {
            finish()
        }

        binding.buttonSave.setOnClickListener {
            saveStation()
        }
    }

    private fun setupWindowInsets() {
        // Apply insets to toolbar
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarAddStation) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Apply insets to ScrollView - add bottom padding for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollViewContent) { v, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                navigationBars.bottom
            )
            insets
        }
    }

    private fun loadStationForEditing() {
        lifecycleScope.launch {
            editingStationId?.let { id ->
                val station = database.radioStationDao().getStationById(id)
                station?.let {
                    binding.editTextName.setText(it.name)
                    binding.editTextUrl.setText(it.streamUrl)
                }
            }
        }
    }


    private fun saveStation() {
        val name = binding.editTextName.text?.toString()?.trim()
        val url = binding.editTextUrl.text?.toString()?.trim()

        when {
            name.isNullOrEmpty() -> {
                binding.textInputLayoutName.error = getString(R.string.enter_name)
                return
            }
            url.isNullOrEmpty() -> {
                binding.textInputLayoutUrl.error = getString(R.string.enter_url)
                return
            }
            !AddStationActivity.isValidUrl(url) -> {
                binding.textInputLayoutUrl.error = getString(R.string.error_invalid_url)
                return
            }
            else -> {
                binding.textInputLayoutName.error = null
                binding.textInputLayoutUrl.error = null
            }
        }

        lifecycleScope.launch {
            try {
                val excludeId = editingStationId ?: 0L
                val existingByName = database.radioStationDao().findStationByName(name, excludeId)
                val existingByUrl = database.radioStationDao().findStationByUrl(url, excludeId)

                when {
                    existingByName != null -> {
                        binding.textInputLayoutName.error = getString(R.string.error_duplicate_name)
                        return@launch
                    }
                    existingByUrl != null -> {
                        binding.textInputLayoutUrl.error = getString(R.string.error_duplicate_url)
                        return@launch
                    }
                }

                val station = if (editingStationId != null) {
                    RadioStation(
                        id = editingStationId!!,
                        name = name,
                        streamUrl = url
                    )
                } else {
                    RadioStation(
                        name = name,
                        streamUrl = url
                    )
                }

                if (editingStationId != null) {
                    database.radioStationDao().updateStation(station)
                    Toast.makeText(this@AddStationActivity, getString(R.string.station_updated), Toast.LENGTH_SHORT).show()
                } else {
                    database.radioStationDao().insertStation(station)
                    Toast.makeText(this@AddStationActivity, getString(R.string.station_saved), Toast.LENGTH_SHORT).show()
                }
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddStationActivity, getString(R.string.save_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"

        @JvmStatic
        internal fun isValidUrl(urlString: String): Boolean {
            return try {
                URL(urlString)
                urlString.startsWith("http://") || urlString.startsWith("https://")
            } catch (e: Exception) {
                false
            }
        }
    }
}

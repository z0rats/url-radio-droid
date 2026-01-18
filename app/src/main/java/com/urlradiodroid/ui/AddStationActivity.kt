package com.urlradiodroid.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddStationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        database = AppDatabase.getDatabase(this)

        binding.buttonSave.setOnClickListener {
            saveStation()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saveStation() {
        val name = binding.editTextName.text?.toString()?.trim()
        val url = binding.editTextUrl.text?.toString()?.trim()

        when {
            name.isNullOrEmpty() -> {
                binding.textInputLayoutName.error = "Введите название"
                return
            }
            url.isNullOrEmpty() -> {
                binding.textInputLayoutUrl.error = "Введите URL"
                return
            }
            !isValidUrl(url) -> {
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
                val station = RadioStation(
                    name = name,
                    streamUrl = url
                )
                database.radioStationDao().insertStation(station)
                Toast.makeText(this@AddStationActivity, "Станция сохранена", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddStationActivity, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidUrl(urlString: String): Boolean {
        return try {
            URL(urlString)
            urlString.startsWith("http://") || urlString.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }
}

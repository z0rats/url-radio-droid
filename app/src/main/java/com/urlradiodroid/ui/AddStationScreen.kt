package com.urlradiodroid.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.urlradiodroid.R
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.ui.theme.URLRadioDroidTheme
import com.urlradiodroid.ui.theme.background_gradient_end
import com.urlradiodroid.ui.theme.background_gradient_mid
import com.urlradiodroid.ui.theme.background_gradient_start
import com.urlradiodroid.ui.theme.card_surface
import com.urlradiodroid.ui.theme.card_surface_active
import com.urlradiodroid.ui.theme.text_primary
import com.urlradiodroid.ui.theme.text_hint
import kotlinx.coroutines.launch
import java.net.URL

class AddStationActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private var editingStationId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = AppDatabase.getDatabase(this)
        editingStationId = intent.getLongExtra(EXTRA_STATION_ID, -1L).takeIf { it != -1L }

        setContent {
            URLRadioDroidTheme {
                AddStationScreen(
                    editingStationId = editingStationId,
                    database = database,
                    onSaveSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"

        @JvmStatic
        internal fun isValidUrl(urlString: String): Boolean {
            if (urlString.isBlank()) return false
            if (urlString.any { it.isWhitespace() }) return false
            return try {
                val url = URL(urlString)
                if (url.host.isNullOrBlank()) return false
                urlString.startsWith("http://") || urlString.startsWith("https://")
            } catch (e: Exception) {
                false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStationScreen(
    editingStationId: Long?,
    database: AppDatabase,
    onSaveSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editingStationId) {
        editingStationId?.let { id ->
            val station = database.radioStationDao().getStationById(id)
            station?.let {
                name = it.name
                url = it.streamUrl
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        background_gradient_start,
                        background_gradient_mid,
                        background_gradient_end
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (editingStationId != null) {
                                stringResource(R.string.edit_station)
                            } else {
                                stringResource(R.string.add_station)
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = card_surface,
                        titleContentColor = text_primary,
                        navigationIconContentColor = text_primary
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    colors = CardDefaults.cardColors(containerColor = card_surface),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                nameError = null
                            },
                            label = { Text(stringResource(R.string.station_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = nameError != null,
                            supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = card_surface_active,
                                unfocusedContainerColor = card_surface_active,
                                focusedTextColor = text_primary,
                                unfocusedTextColor = text_primary,
                                focusedLabelColor = text_hint,
                                unfocusedLabelColor = text_hint,
                                cursorColor = text_primary,
                                focusedIndicatorColor = text_hint,
                                unfocusedIndicatorColor = text_hint.copy(alpha = 0.5f),
                                errorIndicatorColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )

                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                urlError = null
                            },
                            label = { Text(stringResource(R.string.stream_url)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = urlError != null,
                            supportingText = urlError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = card_surface_active,
                                unfocusedContainerColor = card_surface_active,
                                focusedTextColor = text_primary,
                                unfocusedTextColor = text_primary,
                                focusedLabelColor = text_hint,
                                unfocusedLabelColor = text_hint,
                                cursorColor = text_primary,
                                focusedIndicatorColor = text_hint,
                                unfocusedIndicatorColor = text_hint.copy(alpha = 0.5f),
                                errorIndicatorColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Button(
                            onClick = {
                                val nameTrimmed = name.trim()
                                val urlTrimmed = url.trim()

                                when {
                                    nameTrimmed.isEmpty() -> {
                                        nameError = context.getString(R.string.enter_name)
                                    }
                                    urlTrimmed.isEmpty() -> {
                                        urlError = context.getString(R.string.enter_url)
                                    }
                                    !AddStationActivity.isValidUrl(urlTrimmed) -> {
                                        urlError = context.getString(R.string.error_invalid_url)
                                    }
                                    else -> {
                                        (context as? ComponentActivity)?.lifecycleScope?.launch {
                                            try {
                                                val excludeId = editingStationId ?: 0L
                                                val existingByName = database.radioStationDao().findStationByName(nameTrimmed, excludeId)
                                                val existingByUrl = database.radioStationDao().findStationByUrl(urlTrimmed, excludeId)

                                                when {
                                                    existingByName != null -> {
                                                        nameError = context.getString(R.string.error_duplicate_name)
                                                    }
                                                    existingByUrl != null -> {
                                                        urlError = context.getString(R.string.error_duplicate_url)
                                                    }
                                                    else -> {
                                                        val station = if (editingStationId != null) {
                                                            RadioStation(
                                                                id = editingStationId,
                                                                name = nameTrimmed,
                                                                streamUrl = urlTrimmed,
                                                                customIcon = null
                                                            )
                                                        } else {
                                                            RadioStation(
                                                                name = nameTrimmed,
                                                                streamUrl = urlTrimmed,
                                                                customIcon = null
                                                            )
                                                        }

                                                        if (editingStationId != null) {
                                                            database.radioStationDao().updateStation(station)
                                                            Toast.makeText(context, context.getString(R.string.station_updated), Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            database.radioStationDao().insertStation(station)
                                                            Toast.makeText(context, context.getString(R.string.station_saved), Toast.LENGTH_SHORT).show()
                                                        }
                                                        onSaveSuccess()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, context.getString(R.string.save_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}

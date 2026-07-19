package com.freqcast.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freqcast.R
import com.freqcast.data.RadioStationRepository
import com.freqcast.ui.playback.SettingsStore
import com.freqcast.ui.theme.FreqcastTheme
import com.freqcast.ui.theme.Spacing
import com.freqcast.ui.theme.background_gradient_end
import com.freqcast.ui.theme.background_gradient_mid
import com.freqcast.ui.theme.background_gradient_start
import com.freqcast.ui.theme.card_border
import com.freqcast.ui.theme.card_surface
import com.freqcast.ui.theme.glass_accent
import com.freqcast.ui.theme.text_hint
import com.freqcast.ui.theme.text_primary
import com.freqcast.util.StationShare
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = RadioStationRepository.create(this)
        val viewModelFactory = SettingsViewModel.provideFactory(repository)

        setContent {
            FreqcastTheme {
                val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    var warnOnMeteredConnection by remember { mutableStateOf(settingsStore.warnOnMeteredConnection) }

    val exportChooserTitle = stringResource(R.string.export_stations)
    val onExportClick: () -> Unit = {
        coroutineScope.launch {
            try {
                val json = viewModel.exportStationsJson()
                StationShare.share(context, json, exportChooserTitle, "freqcast-stations")
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                try {
                    val content =
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: throw java.io.IOException("Cannot open file")
                    val result = viewModel.importStations(content)
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.import_result, result.imported, result.skipped),
                            Toast.LENGTH_LONG,
                        ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.import_error), Toast.LENGTH_SHORT).show()
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(background_gradient_start, background_gradient_mid, background_gradient_end),
                        ),
                    ).padding(paddingValues)
                    .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(containerColor = card_surface),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_metered_warning), color = text_primary)
                        Text(
                            stringResource(R.string.settings_metered_warning_description),
                            color = text_hint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = warnOnMeteredConnection,
                        onCheckedChange = {
                            warnOnMeteredConnection = it
                            settingsStore.warnOnMeteredConnection = it
                        },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = text_primary,
                                checkedTrackColor = glass_accent,
                                uncheckedThumbColor = text_hint,
                                uncheckedTrackColor = card_surface,
                                uncheckedBorderColor = card_border,
                            ),
                    )
                }
            }

            Card(
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(containerColor = card_surface),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.export_stations), color = text_primary)
                }
            }

            Card(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(containerColor = card_surface),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.import_stations), color = text_primary)
                }
            }
        }
    }
}

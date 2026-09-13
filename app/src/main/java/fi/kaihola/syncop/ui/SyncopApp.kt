package fi.kaihola.syncop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import fi.kaihola.syncop.SyncopViewModel
import fi.kaihola.syncop.Transport
import fi.kaihola.syncop.model.ClickDensity
import fi.kaihola.syncop.model.MeasurementGrid
import fi.kaihola.syncop.ui.theme.Fog
import fi.kaihola.syncop.ui.theme.Paper
import fi.kaihola.syncop.ui.theme.RecordRed
import kotlin.math.roundToInt

@Composable
fun SyncopApp(vm: SyncopViewModel, onRequestPermission: () -> Unit, onExport: () -> Unit) {
    var secondsVisible by rememberSaveable { mutableFloatStateOf(6f) }
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val timeline: @Composable (Modifier) -> Unit = { layoutModifier ->
        Box(layoutModifier.fillMaxWidth()) {
            Timeline(
                session = vm.session,
                revision = vm.revision,
                playhead = vm.playhead,
                secondsVisible = secondsVisible,
                interactive = vm.transport == Transport.STOPPED,
                onScroll = { vm.playhead = it.coerceIn(0, vm.session.length) },
                onZoom = { secondsVisible = it },
            )
            Text(
                "Peak timing: green on beat, yellow/orange = error, red ≥50 ms; grey = no hit",
                Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Fog,
                fontSize = 11.sp,
            )
            StatusLine(vm, Modifier.align(Alignment.TopEnd).padding(12.dp))
            if (!vm.hasPermission) {
                TextButton(onClick = onRequestPermission, Modifier.align(Alignment.Center)) {
                    Text("Allow microphone access to record", color = Paper)
                }
            }
        }
    }
    val controls: @Composable () -> Unit = {
        TempoControl(vm.tempo, vm::changeTempo, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        ClickDensityControl(vm.clickDensity, vm.transport == Transport.STOPPED, vm::changeClickDensity)
        TransportControls(vm, onExport, { showSettings = true }, { confirmErase = true })
    }

    if (landscape) Row(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
    ) {
        timeline(Modifier.weight(1f))
        Column(Modifier.width(280.dp), horizontalAlignment = Alignment.CenterHorizontally) { controls() }
    } else Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
    ) {
        timeline(Modifier.weight(1f))
        controls()
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase recording?") },
            text = { Text("The recorded audio and its markers will be removed.") },
            confirmButton = { TextButton({ vm.erase(); confirmErase = false }) { Text("Erase", color = RecordRed) } },
            dismissButton = { TextButton({ confirmErase = false }) { Text("Keep") } },
        )
    }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun ClickDensityControl(density: ClickDensity, enabled: Boolean, onDensity: (ClickDensity) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text("Clicks: ${density.label}")
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ClickDensity.entries.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onDensity(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun TransportControls(vm: SyncopViewModel, onExport: () -> Unit, onSettings: () -> Unit, onErase: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSettings, Modifier.size(48.dp)) {
            Icon(Icons.Filled.Tune, "Settings", tint = Fog)
        }
        TransportButton(
            icon = if (vm.transport == Transport.PLAYING) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            label = "Play", color = Paper, enabled = vm.session.length > 0,
            onClick = vm::togglePlay,
        )
        TransportButton(
            icon = if (vm.transport == Transport.RECORDING) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
            label = "Record", color = RecordRed, enabled = vm.hasPermission, large = true,
            onClick = vm::toggleRecord,
        )
        IconButton(onClick = onExport, Modifier.size(48.dp), enabled = vm.session.length > 0 && vm.transport == Transport.STOPPED) {
            Icon(Icons.Filled.IosShare, "Export WAV", tint = Fog)
        }
        IconButton(onClick = onErase, Modifier.size(48.dp), enabled = vm.session.length > 0) {
            Icon(Icons.Filled.Delete, "Erase", tint = Fog)
        }
    }
}

@Composable
private fun StatusLine(vm: SyncopViewModel, modifier: Modifier) {
    val secs = vm.playhead.toDouble() / fi.kaihola.syncop.model.SAMPLE_RATE
    val text = when (vm.transport) {
        Transport.RECORDING -> "● REC  %d:%04.1f".format((secs / 60).toInt(), secs % 60)
        Transport.PLAYING -> "▶  %d:%04.1f".format((secs / 60).toInt(), secs % 60)
        Transport.STOPPED -> "%d:%04.1f".format((secs / 60).toInt(), secs % 60)
    }
    Text(text, modifier, color = if (vm.transport == Transport.RECORDING) RecordRed else Fog, fontSize = 14.sp, letterSpacing = 1.sp)
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color,
    enabled: Boolean, large: Boolean = false, onClick: () -> Unit,
) {
    val size = if (large) 76.dp else 60.dp
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(size).background(Color.White.copy(alpha = 0.06f), CircleShape),
    ) {
        Icon(icon, label, tint = if (enabled) color else Fog.copy(alpha = 0.4f), modifier = Modifier.size(if (large) 40.dp else 32.dp))
    }
}

@Composable
private fun SettingsDialog(vm: SyncopViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Clicks during playback")
                    Switch(vm.clicksOnPlayback, { vm.clicksOnPlayback = it })
                }
                Spacer(Modifier.height(16.dp))
                Text("Input latency offset: ${vm.manualLatencyMs} ms")
                Slider(
                    value = vm.manualLatencyMs.toFloat(),
                    onValueChange = { vm.manualLatencyMs = it.roundToInt() },
                    valueRange = -100f..300f,
                )
                GridControl(vm.measurementGrid, vm.transport == Transport.STOPPED, vm::changeMeasurementGrid)
                val auto = vm.autoLatencyMs
                Text(
                    if (auto == null) "Auto-calibration: no click bleed detected yet (use the speaker to calibrate)"
                    else "Auto-calibration: %.0f ms measured from click bleed".format(auto),
                    color = Fog, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("Markers: green on the beat, red at ±50 ms.", color = Fog, fontSize = 13.sp)
            }
        },
    )
}

@Composable
private fun GridControl(grid: MeasurementGrid, enabled: Boolean, onGrid: (MeasurementGrid) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text("Measure claps: ${grid.label}") }
        androidx.compose.material3.DropdownMenu(expanded, { expanded = false }) {
            MeasurementGrid.entries.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(option.label) }, onClick = { onGrid(option); expanded = false })
            }
        }
    }
}

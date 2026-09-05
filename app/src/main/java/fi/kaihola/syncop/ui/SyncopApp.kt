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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.kaihola.syncop.SyncopViewModel
import fi.kaihola.syncop.Transport
import fi.kaihola.syncop.ui.theme.Fog
import fi.kaihola.syncop.ui.theme.Paper
import fi.kaihola.syncop.ui.theme.RecordRed
import kotlin.math.roundToInt

@Composable
fun SyncopApp(vm: SyncopViewModel, onRequestPermission: () -> Unit, onExport: () -> Unit) {
    var secondsVisible by rememberSaveable { mutableFloatStateOf(6f) }
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Timeline(
                session = vm.session,
                revision = vm.revision,
                playhead = vm.playhead,
                secondsVisible = secondsVisible,
                interactive = vm.transport == Transport.STOPPED,
                onScroll = { vm.playhead = it.coerceIn(0, vm.session.length) },
                onZoom = { secondsVisible = it },
            )
            StatusLine(vm, Modifier.align(Alignment.TopEnd).padding(12.dp))
            if (!vm.hasPermission) {
                TextButton(onClick = onRequestPermission, Modifier.align(Alignment.Center)) {
                    Text("Allow microphone access to record", color = Paper)
                }
            }
        }

        TempoControl(vm.tempo, vm::changeTempo, Modifier.fillMaxWidth().padding(horizontal = 16.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showSettings = true }, Modifier.size(48.dp)) {
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
            IconButton(onClick = { confirmErase = true }, Modifier.size(48.dp), enabled = vm.session.length > 0) {
                Icon(Icons.Filled.Delete, "Erase", tint = Fog)
            }
        }
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

package fi.kaihola.syncop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.kaihola.syncop.SyncopViewModel
import fi.kaihola.syncop.ui.theme.Fog
import fi.kaihola.syncop.ui.theme.Paper
import androidx.compose.foundation.Canvas
import kotlin.math.roundToInt

/**
 * Always-visible tempo control: a big number flanked by − / + buttons, sitting on a ruler
 * strip that can be dragged horizontally (about 1 BPM per 6 dp) for fast, continuous changes.
 */
@Composable
fun TempoControl(tempo: Int, onTempo: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val pxPerBpm = with(density) { 6.dp.toPx() }
    val accumulator = remember { floatArrayOf(0f) }
    val currentTempo = rememberUpdatedState(tempo)
    val currentOnTempo = rememberUpdatedState(onTempo)

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(pxPerBpm) {
                var dragTempo = currentTempo.value
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulator[0] = 0f
                        dragTempo = currentTempo.value
                    },
                ) { _, dragAmount ->
                    accumulator[0] += dragAmount
                    val steps = (accumulator[0] / pxPerBpm).toInt()
                    if (steps != 0) {
                        accumulator[0] -= steps * pxPerBpm
                        dragTempo += steps
                        currentOnTempo.value(dragTempo)
                    }
                }
            }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Filled.Remove, "Slower", enabled = tempo > SyncopViewModel.MIN_TEMPO) { onTempo(tempo - 1) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$tempo", fontSize = 64.sp, fontWeight = FontWeight.Light, color = Paper, lineHeight = 64.sp)
                Text("BPM", fontSize = 13.sp, color = Fog, letterSpacing = 2.sp)
            }
            RoundButton(Icons.Filled.Add, "Faster", enabled = tempo < SyncopViewModel.MAX_TEMPO) { onTempo(tempo + 1) }
        }
        TempoRuler(tempo, Modifier.fillMaxWidth().height(28.dp).padding(top = 8.dp), pxPerBpm)
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.06f), CircleShape),
    ) {
        Icon(icon, label, tint = if (enabled) Paper else Fog.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
    }
}

/** Ticks that slide under the number as the tempo changes, hinting that the strip is draggable. */
@Composable
private fun TempoRuler(tempo: Int, modifier: Modifier, pxPerBpm: Float) {
    Canvas(modifier) {
        val centre = size.width / 2
        val visible = (centre / pxPerBpm).toInt() + 1
        for (d in -visible..visible) {
            val bpm = tempo + d
            if (bpm < SyncopViewModel.MIN_TEMPO || bpm > SyncopViewModel.MAX_TEMPO) continue
            val x = centre + d * pxPerBpm
            val major = bpm % 10 == 0
            val h = if (major) size.height else size.height * 0.45f
            val fade = 1f - (kotlin.math.abs(x - centre) / centre).coerceIn(0f, 1f)
            drawLine(
                color = Fog.copy(alpha = (if (major) 0.9f else 0.45f) * fade),
                start = Offset(x, size.height - h),
                end = Offset(x, size.height),
                strokeWidth = if (major) 2f else 1f,
            )
        }
        drawLine(Paper, Offset(centre, 0f), Offset(centre, size.height), strokeWidth = 3f)
    }
}

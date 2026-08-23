package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.math.roundToInt

data class FloatingNote(
    var x: Float,
    val noteNumber: Int,
    val name: String
)

@Composable
fun MelodyVisualizer(pitchEvent: PitchEvent, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Store active notes
    val notes = remember { mutableStateListOf<FloatingNote>() }

    // Waveform phase for animation
    var phase by remember { mutableFloatStateOf(0f) }

    // Animation loop for moving notes and wave
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { _ ->
                phase -= 0.1f // Animate wave
                
                // Move notes right
                val iterator = notes.iterator()
                while (iterator.hasNext()) {
                    val note = iterator.next()
                    note.x += 3f // speed moving right
                    if (note.x > 3000f) { // far right bounds
                        iterator.remove()
                    }
                }
            }
        }
    }

    // Add notes when pitch is detected (throttle to avoid overlap)
    var lastNoteTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pitchEvent) {
        if (!pitchEvent.isSilence && pitchEvent.probability > 0.85f) {
            val now = System.currentTimeMillis()
            if (now - lastNoteTime > 250) { // spawn a note every 250ms max
                notes.add(FloatingNote(1000f, pitchEvent.noteNumber, pitchEvent.noteName))
                lastNoteTime = now
            }
        }
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(Color(0xFFFFF9F9))) { // Very light warm background
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f
            val staffLineSpacing = 40.dp.toPx()

            // Draw Staff Lines (5 lines)
            val topStaffY = centerY - (2 * staffLineSpacing)
            for (i in 0..4) {
                val y = topStaffY + (i * staffLineSpacing)
                drawLine(
                    color = Color.Red.copy(alpha = 0.2f),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 2f
                )
            }

            // Draw Audio Wave on the left to simulate the "mouth/source"
            val wavePath = Path()
            val waveWidth = 300f
            val amplitude = if (pitchEvent.isSilence) 10f else 100f * pitchEvent.probability
            
            wavePath.moveTo(0f, centerY)
            for (x in 0..waveWidth.toInt() step 5) {
                val normalizedX = x / waveWidth
                // Fade out wave towards the right
                val envelope = 1f - normalizedX
                val y = centerY + sin(x * 0.05f + phase) * amplitude * envelope
                wavePath.lineTo(x.toFloat(), y)
            }

            drawPath(
                path = wavePath,
                color = Color.Red.copy(alpha = 0.8f),
                style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw Floating Notes
            val baseNoteNumber = 69 // A4 - Center of the staff visually roughly
            
            notes.toList().forEach { note ->
                // Ensure new notes start at the end of the wave initially
                if(note.x == 1000f) note.x = waveWidth

                // Calculate vertical position relative to staff
                // Higher note -> lower Y coordinate
                val noteDiff = note.noteNumber - baseNoteNumber
                // Each half step moves roughly a fraction, let's say 2 steps = 1 line (approx)
                val noteY = centerY - (noteDiff * (staffLineSpacing / 2f))

                // Draw Note head (circle)
                drawCircle(
                    color = Color.Red,
                    radius = 15.dp.toPx(),
                    center = Offset(note.x, noteY)
                )

                // Draw Note Stem
                drawLine(
                    color = Color.Red,
                    start = Offset(note.x + 15.dp.toPx(), noteY),
                    end = Offset(note.x + 15.dp.toPx(), noteY - 50.dp.toPx()),
                    strokeWidth = 4.dp.toPx()
                )

                // Draw Text (Note Name)
                if (note.name.isNotEmpty()) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = note.name,
                        topLeft = Offset(note.x - 10.dp.toPx(), noteY + 20.dp.toPx()),
                        style = TextStyle(
                            color = Color.Red,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Overlay status text
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (pitchEvent.isSilence) "Waiting for voice..." else "${pitchEvent.pitchHz.roundToInt()} Hz",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Red.copy(alpha = 0.7f),
                fontWeight = FontWeight.Light
            )
            Text(
                text = pitchEvent.noteName,
                style = MaterialTheme.typography.displayLarge,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

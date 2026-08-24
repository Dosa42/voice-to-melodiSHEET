package com.example

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.log10
import kotlin.math.max

// Magma-style heatmap colors from dark/black to white
private fun magnitudeToColor(magnitude: Float): Int {
    // Convert magnitude to decibels
    val db = 20 * log10(magnitude + 1e-6f)
    
    // Normalize dB between -80 (quiet) and 0 (loud)
    val minDb = -80f
    val maxDb = 0f
    val normalized = max(0f, (db - minDb) / (maxDb - minDb))
    
    // Interpolate colors based on intensity
    val t = normalized.coerceIn(0f, 1f)
    
    // Black -> Purple -> Orange -> Yellow -> White
    val r = (when {
        t < 0.25f -> t * 4 * 128
        t < 0.5f -> 128 + (t - 0.25f) * 4 * 127
        t < 0.75f -> 255f
        else -> 255f
    }).toInt().coerceIn(0, 255)

    val g = (when {
        t < 0.25f -> 0f
        t < 0.5f -> (t - 0.25f) * 4 * 128
        t < 0.75f -> 128 + (t - 0.5f) * 4 * 127
        else -> 255f
    }).toInt().coerceIn(0, 255)

    val b = (when {
        t < 0.25f -> t * 4 * 128
        t < 0.5f -> 128f - (t - 0.25f) * 4 * 128
        t < 0.75f -> 0f
        else -> (t - 0.75f) * 4 * 255
    }).toInt().coerceIn(0, 255)

    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

@Composable
fun SpectrogramVisualizer(magnitudes: FloatArray, modifier: Modifier = Modifier) {
    // 512 frequency bins from a 1024-point FFT
    val numBins = 512
    // Width of the rolling spectrogram buffer in pixels
    val historyWidth = 400

    val bitmap = remember { Bitmap.createBitmap(historyWidth, numBins, Bitmap.Config.ARGB_8888) }
    val pixels = remember { IntArray(historyWidth * numBins) }

    LaunchedEffect(magnitudes) {
        if (magnitudes.isEmpty()) return@LaunchedEffect

        // 1. Shift all existing pixels one column to the left
        for (y in 0 until numBins) {
            val rowStart = y * historyWidth
            System.arraycopy(pixels, rowStart + 1, pixels, rowStart, historyWidth - 1)
        }

        // 2. Draw the new magnitudes in the right-most column
        for (y in 0 until numBins) {
            // Low frequencies at the bottom (highest Y index in bitmap), high at the top
            val frequencyBin = numBins - 1 - y
            // We clamp frequency reading to avoid out-of-bounds in case array is smaller
            val mag = if (frequencyBin < magnitudes.size) magnitudes[frequencyBin] else 0f
            val color = magnitudeToColor(mag)
            pixels[y * historyWidth + (historyWidth - 1)] = color
        }

        // 3. Update the Android Bitmap
        bitmap.setPixels(pixels, 0, historyWidth, 0, 0, historyWidth, numBins)
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)) {
        
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Spectrogram",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        
        Text(
            text = "SPECTROGRAM",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}

package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
  @OptIn(ExperimentalPermissionsApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

          if (permissionState.status.isGranted) {
            val audioAnalyzer = remember { AudioAnalyzer() }
            
            DisposableEffect(Unit) {
              audioAnalyzer.start()
              onDispose {
                audioAnalyzer.stop()
              }
            }

            val pitchEvent by audioAnalyzer.pitchFlow.collectAsState()

            MelodyVisualizer(
              pitchEvent = pitchEvent,
              modifier = Modifier.padding(innerPadding)
            )
          } else {
            Box(
              modifier = Modifier.fillMaxSize().padding(innerPadding),
              contentAlignment = Alignment.Center
            ) {
              androidx.compose.material3.Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text("Grant Microphone Permission")
              }
            }
          }
        }
      }
    }
  }
}

package com.app.speedtracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.app.speedtracker.services.SpeedMonitorService


class MainActivity : ComponentActivity() {

    private var speed by mutableDoubleStateOf(0.0)
    private var speedLimitInput by mutableStateOf(TextFieldValue("50"))

    private val speedUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                speed = it.getDoubleExtra("EXTRA_SPEED", 0.0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        setContent {
            SpeedMonitorApp(speed, speedLimitInput, onSpeedLimitChange = { newLimit ->
                speedLimitInput = newLimit
            }, onSetSpeedLimit = {
                setSpeedLimit(speedLimitInput.text.toDoubleOrNull() ?: 50.0)
            }, onStopService = {
                Intent(this, SpeedMonitorService::class.java).also { intent ->
                    stopService(intent)
                }
            })
        }
    }

    private fun checkAndRequestPermissions() {
        when {
            // Check if permissions are already granted
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Both permissions are granted
                startService()
            }

            // Permissions are not granted, request them
            else -> {
                requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                startService()
            } else {
                // One or both permissions are denied
                Toast.makeText(baseContext, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private fun startService() {
        Intent(this, SpeedMonitorService::class.java).also { intent ->
            startForegroundService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Register receiver for speed updates
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                speedUpdateReceiver, IntentFilter("com.example.speedmonitor.SPEED_UPDATE"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                speedUpdateReceiver,
                IntentFilter("com.example.speedmonitor.SPEED_UPDATE")
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister receiver to avoid memory leaks
        unregisterReceiver(speedUpdateReceiver)
    }

    private fun setSpeedLimit(newLimit: Double) {
        // Send intent to service with the new speed limit
        Intent(this, SpeedMonitorService::class.java).also { intent ->
            intent.putExtra("EXTRA_NEW_SPEED_LIMIT", newLimit)
            startForegroundService(intent)
        }
    }
}

@Composable
fun SpeedMonitorApp(
    speed: Double, speedLimitInput: TextFieldValue,
    onSpeedLimitChange: (TextFieldValue) -> Unit,
    onSetSpeedLimit: () -> Unit,
    onStopService: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center // Center the CircularSpeedView
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = speedLimitInput,
                onValueChange = onSpeedLimitChange,
                singleLine = true,
                label = { Text("Set Speed Limit (km/h)", color = Color.White) }, // Label color
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White), // Text color
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .focusRequester(FocusRequester()),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // Clear focus on "Done" action
                        focusManager.clearFocus()
                    }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onSetSpeedLimit) {
                Text(text = "Set Speed Limit")
            }
            Spacer(modifier = Modifier.height(32.dp))
            CircularSpeedView(speed)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onStopService) {
                Text(text = "Stop Service")
            }
        }
    }
}

@Composable
fun CircularSpeedView(speed: Double) {
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%.1f km/h", speed),
            color = Color.White,
            fontSize = 32.sp, // Adjust font size as needed
            textAlign = TextAlign.Center
        )
    }
}

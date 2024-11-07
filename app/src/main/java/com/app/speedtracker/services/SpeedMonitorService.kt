package com.app.speedtracker.services
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.app.speedtracker.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class SpeedMonitorService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var vibrator: Vibrator
    private var speedLimit = 50.0 // Speed limit in km/h (example)

    override fun onCreate() {
        super.onCreate()

        // Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Start foreground service with notification
        createNotificationChannel()
        startForeground(1, buildNotification())

        // Start location updates
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val newSpeedLimit = it.getDoubleExtra("EXTRA_NEW_SPEED_LIMIT", 50.0)
            if (newSpeedLimit >= 0) {
                Toast.makeText(baseContext, "New speed limit set: $newSpeedLimit", Toast.LENGTH_SHORT).show()
                speedLimit = newSpeedLimit
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            "SpeedMonitorChannel",
            "Speed Monitor Channel",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "SpeedMonitorChannel")
            .setContentTitle("Speed Monitor Running")
            .setContentText("Monitoring your speed...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                handleNewLocation(location)
            }
        }
    }

    private fun handleNewLocation(location: Location) {
        val speed = location.speed * 3.6 // Convert m/s to km/h
        checkSpeedAndVibrate(speed)
        sendSpeedUpdate(speed)
    }

    private fun checkSpeedAndVibrate(speed: Double) {
        when {
            speed <= speedLimit - 5 -> {
                // Vibrate pattern for speed well below the limit
                vibrate(100, 300)
            }

            speed > speedLimit -> {
                // Vibrate pattern for speed above the limit
                vibrate(500, 1000)
            }
        }
    }

    private fun vibrate(onDuration: Long, offDuration: Long) {
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, onDuration, offDuration), -1)
        )
    }

    private fun sendSpeedUpdate(speed: Double) {
        val intent = Intent("com.example.speedmonitor.SPEED_UPDATE")
        intent.putExtra("EXTRA_SPEED", speed)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

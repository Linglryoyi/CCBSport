package com.ccbsport.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class MapLocation(val point: RoutePoint, val heading: Float?)

@SuppressLint("MissingPermission")
@Composable
internal fun rememberMapLocation(enabled: Boolean): MapLocation? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var position by remember { mutableStateOf<Location?>(null) }
    var azimuth by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context, lifecycle, enabled, fine, coarse) {
        if (!enabled || (!fine && !coarse)) {
            position = null
            azimuth = null
            onDispose {}
        } else {
            val locations = context.getSystemService(LocationManager::class.java)
            val sensors = context.getSystemService(SensorManager::class.java)
            val providers = if (fine) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                else listOf(LocationManager.NETWORK_PROVIDER)
            val listener = object : LocationListener {
                override fun onLocationChanged(fix: Location) {
                    val previous = position
                    val age = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
                    if (age <= 120_000 && (previous == null || fix.elapsedRealtimeNanos >= previous.elapsedRealtimeNanos &&
                        (fix.accuracy <= previous.accuracy * 2 || fix.elapsedRealtimeNanos - previous.elapsedRealtimeNanos > 10_000_000_000L))
                    ) position = fix
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Required on older Android versions")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            val compass = object : SensorEventListener {
                private val matrix = FloatArray(9)
                private val angles = FloatArray(3)
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, angles)
                    val next = Math.toDegrees(angles[0].toDouble()).toFloat()
                    val previous = azimuth
                    if (previous == null || kotlin.math.abs(next - previous) >= 3f) azimuth = next
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        providers.forEach { provider ->
                            runCatching {
                                locations.getLastKnownLocation(provider)?.let(listener::onLocationChanged)
                                locations.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
                            }
                        }
                        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
                            sensors.registerListener(compass, it, 150_000)
                        }
                    }
                    Lifecycle.Event.ON_STOP -> {
                        locations.removeUpdates(listener)
                        sensors.unregisterListener(compass)
                    }
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                locations.removeUpdates(listener)
                sensors.unregisterListener(compass)
            }
        }
    }
    val fix = position ?: return null
    if (!enabled || (!fine && !coarse)) return null
    val magnetic = azimuth
    val declination = remember(fix) {
        GeomagneticField(fix.latitude.toFloat(), fix.longitude.toFloat(), fix.altitude.toFloat(), fix.time).declination
    }
    val heading = when {
        SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos <= 8_000_000_000L &&
            fix.hasSpeed() && fix.speed >= 1.2f && fix.hasBearing() &&
            (!fix.hasBearingAccuracy() || fix.bearingAccuracyDegrees <= 45f) -> fix.bearing
        magnetic != null -> (magnetic + declination + 360f) % 360f
        else -> null
    }
    return MapLocation(RoutePoint(fix.latitude, fix.longitude), heading)
}

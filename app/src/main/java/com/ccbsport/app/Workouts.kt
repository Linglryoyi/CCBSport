package com.ccbsport.app

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

enum class Sport(val label: Int, val hint: Int) {
    Outdoor(R.string.outdoor, R.string.outdoor_hint),
    Indoor(R.string.indoor, R.string.indoor_hint),
    Fitness(R.string.fitness, R.string.fitness_hint),
    Walk(R.string.walk, R.string.walk_hint)
}

data class RoutePoint(val lat: Double, val lon: Double)

data class Workout(
    val sport: Sport,
    val date: Long,
    val seconds: Long,
    val meters: Double,
    val note: String = "",
    val points: List<RoutePoint> = emptyList(),
    val id: String = UUID.randomUUID().toString(),
    val steps: Int = 0,
    val estimatedMeters: Double = 0.0
)

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("ccbsport", Context.MODE_PRIVATE)

    fun read(): List<Workout> = runCatching {
        WorkoutJson.decode(JSONArray(prefs.getString("workouts", "[]"))).sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    fun write(records: List<Workout>): Boolean {
        return prefs.edit().putString("workouts", WorkoutJson.encode(records).toString()).commit()
    }

    fun name() = prefs.getString("name", "") ?: ""
    fun avatar() = prefs.getString("avatar", "") ?: ""
    fun hasCompletedMapSetup(): Boolean = prefs.getBoolean("offline_map_setup_complete", prefs.contains("language"))
    fun completeMapSetup(): Boolean = prefs.edit().putBoolean("offline_map_setup_complete", true).commit()
    fun theme() = AppThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) }
        ?: AppThemeMode.System
    fun saveTheme(mode: AppThemeMode) { prefs.edit().putString("theme", mode.name).apply() }
    fun clear(): Boolean = prefs.edit().clear().commit()
    fun language() = AppLanguage.entries.firstOrNull { it.tag == prefs.getString("language", null) }
    fun saveLanguage(language: AppLanguage) {
        prefs.edit().apply {
            // An existing installation already has a language and access to offline map settings.
            if (!prefs.contains("offline_map_setup_complete")) {
                putBoolean("offline_map_setup_complete", prefs.contains("language"))
            }
            putString("language", language.tag)
        }.apply()
    }
    fun stride() = prefs.getFloat("stride", 0.9f).toDouble()
    fun saveStride(meters: Double) { prefs.edit().putFloat("stride", meters.toFloat()).apply() }
    fun saveProfile(name: String, avatar: String) {
        prefs.edit().putString("name", name).putString("avatar", avatar).apply()
    }
}

data class Session(
    val sport: Sport,
    val date: Long = System.currentTimeMillis(),
    val started: Long = SystemClock.elapsedRealtime(),
    val tracking: Boolean = false,
    val meters: Double = 0.0,
    val points: List<RoutePoint> = emptyList(),
    val counting: Boolean = false,
    val gpsFixAt: Long = 0L,
    val steps: Int = 0,
    val estimatedMeters: Double = 0.0
) {
    fun seconds() = ((SystemClock.elapsedRealtime() - started) / 1000).coerceAtLeast(0)
    fun hasRecentFix() = gpsFixAt != 0L && SystemClock.elapsedRealtime() - gpsFixAt <= 8000
}

object ActiveWorkout {
    val current = MutableStateFlow<Session?>(null)
}

fun Context.stepSensor(): Sensor? = getSystemService(SensorManager::class.java).let {
    it.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: it.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
}

fun Context.canCountSteps() = Build.VERSION.SDK_INT < 29 ||
    checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

class RouteService : Service(), LocationListener, SensorEventListener {
    private lateinit var locations: LocationManager
    private lateinit var sensors: SensorManager
    private var last: Location? = null
    private var fixAt = 0L
    private var counter: Int? = null
    private var estimatedAfterFix = false
    private var stride = .9
    private var started = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = ActiveWorkout.current.value
        if (session == null || (!session.tracking && !session.counting)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (session.started == started) return START_NOT_STICKY
        started = session.started
        last = null
        fixAt = 0
        counter = null
        estimatedAfterFix = false
        stride = if (session.sport == Sport.Walk) .70 else Store(this).stride()
        val language = Store(this).language() ?: AppLanguage.Chinese
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel("route", language.text(this, R.string.records_title), NotificationManager.IMPORTANCE_LOW)
        )
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val notification = Notification.Builder(this, "route")
                .setSmallIcon(R.drawable.ic_app).setContentTitle("CCBSport · " + language.text(this, session.sport.label))
                .setContentText(language.text(this, R.string.tracking_workout)).setContentIntent(launch).setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) {
                var type = if (session.tracking) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                if (Build.VERSION.SDK_INT >= 34 && session.counting) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                startForeground(1, notification, type)
            } else startForeground(1, notification)
            var tracking = session.tracking
            var counting = session.counting
            if (tracking) {
                locations = getSystemService(LocationManager::class.java)
                tracking = runCatching {
                    locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 0f, this)
                }.isSuccess
            }
            if (counting) {
                sensors = getSystemService(SensorManager::class.java)
                counting = runCatching {
                    stepSensor()?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) } == true
                }.getOrDefault(false)
            }
            ActiveWorkout.current.value = session.copy(tracking = tracking, counting = counting)
            if (!tracking && !counting) stopSelf()
        } catch (_: RuntimeException) {
            ActiveWorkout.current.value = session.copy(tracking = false, counting = false)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val session = ActiveWorkout.current.value ?: return
        if (session.started != started || !session.tracking || !location.hasAccuracy() || location.accuracy > 25f) return
        val timestamp = location.elapsedRealtimeNanos / 1_000_000
        if (timestamp < session.started || timestamp <= fixAt) return
        val previous = last
        val delta = previous?.distanceTo(location)?.toDouble() ?: 0.0
        var addDistance = false
        if (previous != null) {
            val seconds = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1e9
            if (!estimatedAfterFix && seconds > 0 && seconds <= 30 && delta / seconds > 12) return
            addDistance = !estimatedAfterFix && seconds in 0.01..30.0 && delta >= 4
        }
        fixAt = timestamp
        val addPoint = previous == null || estimatedAfterFix || addDistance || delta >= 4
        if (addPoint) last = location
        estimatedAfterFix = false
        ActiveWorkout.current.value = session.copy(
            gpsFixAt = timestamp,
            meters = session.meters + if (addDistance) delta else 0.0,
            points = if (addPoint) session.points + RoutePoint(location.latitude, location.longitude) else session.points
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        val session = ActiveWorkout.current.value ?: return
        if (session.started != started || !session.counting) return
        val timestamp = event.timestamp / 1_000_000
        val steps = if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            if (timestamp >= session.started) 1 else 0
        } else {
            val total = event.values[0].toInt()
            val previous = counter
            counter = total
            if (previous == null || timestamp < session.started) 0 else (total - previous).coerceAtLeast(0)
        }
        if (steps == 0) return
        val hasGps = session.tracking && fixAt != 0L && timestamp - fixAt <= 8000
        val estimate = if (hasGps) 0.0 else steps * stride
        if (!hasGps) estimatedAfterFix = true
        ActiveWorkout.current.value = session.copy(
            steps = session.steps + steps, meters = session.meters + estimate,
            estimatedMeters = session.estimatedMeters + estimate
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Deprecated("Required by Android 9 and earlier")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) {
        last = null
        fixAt = 0
        ActiveWorkout.current.value?.takeIf { it.started == started }?.let {
            ActiveWorkout.current.value = it.copy(gpsFixAt = 0)
        }
    }

    override fun onDestroy() {
        if (::locations.isInitialized) locations.removeUpdates(this)
        if (::sensors.isInitialized) sensors.unregisterListener(this)
        super.onDestroy()
    }
}

fun clock(seconds: Long) = String.format(
    Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60
)

fun km(meters: Double) = String.format(Locale.ROOT, "%.2f", meters / 1000)
fun dateLabel(millis: Long) = SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.ROOT).format(Date(millis))
fun pace(seconds: Long, meters: Double): String {
    if (meters < 1) return "—"
    val split = (seconds / (meters / 1000)).roundToInt()
    return "%d′%02d″".format(Locale.ROOT, split / 60, split % 60)
}

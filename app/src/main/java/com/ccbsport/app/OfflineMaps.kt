package com.ccbsport.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.net.ssl.HttpsURLConnection

data class MapCity(val id: String, val name: String, val region: String, val packageId: String, val size: String)
data class OfflineMap(val id: String, val label: String, val file: File, val bounds: BoundingBox)
data class MapTransfer(val label: String, val received: Long = 0, val total: Long = -1)

class OfflineMaps private constructor(private val context: Context) {
    private val directory = File(context.filesDir, "offline-maps").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("offline-maps", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val installed = MutableStateFlow<List<OfflineMap>>(emptyList())
    val maps = installed.asStateFlow()
    private val selected = MutableStateFlow(preferences.getStringSet("cities", emptySet()).orEmpty().toSet())
    val cities = selected.asStateFlow()
    private val moving = MutableStateFlow<MapTransfer?>(null)
    val transfer = moving.asStateFlow()
    private val failure = MutableStateFlow<Int?>(null)
    val error = failure.asStateFlow()
    private var operation: Job? = null

    val catalog: List<MapCity> by lazy {
        context.assets.open("map-cities.txt").bufferedReader().useLines { lines ->
            lines.map { line ->
                val (id, name, region, packageId, size) = line.split('|')
                MapCity(id, name, region, packageId, size)
            }.toList()
        }
    }

    init {
        operation = scope.launch {
            installed.value = withContext(Dispatchers.IO) {
                directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
                readInstalled()
            }
        }
    }

    fun add(city: MapCity) {
        if (moving.value != null) return
        selected.value = selected.value + city.id
        preferences.edit().putStringSet("cities", selected.value).apply()
        if (installed.value.any { it.id == city.packageId }) return
        install(city.packageId, city.region, imported = false, R.string.offline_download_failed) { target ->
            val url = URL("https://download.mapsforge.org/maps/v5/asia/china/${city.packageId}.map")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "CCBSport/${BuildConfig.VERSION_NAME} (com.ccbsport.app)")
            try {
                if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
                connection.inputStream.use { copy(it, target, connection.contentLengthLong, city.region) }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun import(uri: Uri) {
        if (moving.value != null) return
        val resolver = context.contentResolver
        val id = "import-" + java.util.UUID.randomUUID()
        val label = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()?.take(100) ?: "Mapsforge"
        install(id, label, imported = true, R.string.offline_import_failed) { target ->
            val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val input = resolver.openInputStream(uri) ?: throw IOException("No document stream")
            input.use { copy(it, target, size, label) }
        }
    }

    private fun install(
        id: String, label: String, imported: Boolean, errorId: Int, write: suspend (File) -> Unit
    ) {
        failure.value = null
        moving.value = MapTransfer(label)
        val initial = operation
        operation = scope.launch {
            try {
                initial?.join()
                val map = withContext(Dispatchers.IO) {
                    val target = File(directory, "$id.map")
                    val temp = File(directory, "$id.part")
                    try {
                        write(temp)
                        val bounds = readBounds(temp)
                        ensureActive()
                        if (imported) preferences.edit().putString(id, label).commit()
                        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        OfflineMap(id, label, target, bounds)
                    } finally {
                        temp.delete()
                    }
                }
                installed.value = installed.value.filterNot { it.id == id } + map
            } catch (_: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { installed.value = readInstalled() }
            } catch (_: Exception) {
                failure.value = errorId
            } finally {
                moving.value = null
            }
        }
    }

    private suspend fun copy(input: java.io.InputStream, target: File, total: Long, label: String) {
        if (total > directory.usableSpace) throw IOException("Not enough storage")
        val buffer = ByteArray(64 * 1024)
        var bytes = 0L
        var reported = 0L
        moving.value = MapTransfer(label, 0, total)
        target.outputStream().buffered().use { output ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                bytes += count
                if (bytes - reported >= 256 * 1024) {
                    moving.value = MapTransfer(label, bytes, total)
                    reported = bytes
                }
            }
        }
        if (total >= 0 && bytes != total) throw IOException("Incomplete map")
    }

    fun cancel() { if (moving.value != null) operation?.cancel() }
    fun clearError() { failure.value = null }

    fun removeCity(city: MapCity) {
        selected.value = selected.value - city.id
        preferences.edit().putStringSet("cities", selected.value).apply()
    }

    fun clearCities() {
        selected.value = emptySet()
        preferences.edit().remove("cities").apply()
    }

    fun delete(map: OfflineMap) {
        if (moving.value != null) return
        failure.value = null
        scope.launch {
            val removed = withContext(Dispatchers.IO) { map.file.delete() || !map.file.exists() }
            if (removed) {
                installed.value = installed.value.filterNot { it.id == map.id }
                preferences.edit().remove(map.id).apply()
            } else failure.value = R.string.offline_delete_failed
        }
    }

    private fun readInstalled(): List<OfflineMap> {
        val labels = catalog.associate { it.packageId to it.region }
        return directory.listFiles().orEmpty().filter { it.extension == "map" }.mapNotNull { file ->
            runCatching {
                val id = file.nameWithoutExtension
                OfflineMap(id, labels[id] ?: preferences.getString(id, file.name).orEmpty(),
                    file, readBounds(file))
            }.getOrNull()
        }
    }

    private fun readBounds(file: File): BoundingBox {
        val map = MapFile(file)
        return try { map.boundingBox() } finally { map.close() }
    }

    companion object {
        @Volatile private var instance: OfflineMaps? = null
        fun get(context: Context): OfflineMaps = instance ?: synchronized(this) {
            instance ?: OfflineMaps(context.applicationContext).also { instance = it }
        }
    }
}

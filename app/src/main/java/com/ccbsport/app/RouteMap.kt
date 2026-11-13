package com.ccbsport.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Join
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.overlay.FixedPixelCircle
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import org.mapsforge.map.view.InputListener
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RouteMap(points: List<RoutePoint>, modifier: Modifier = Modifier, live: Boolean = false) {
    val context = LocalContext.current
    val maps by remember(context) { OfflineMaps.get(context) }.maps.collectAsState()
    val current = rememberMapLocation(live && maps.isNotEmpty())
    val initial = remember(maps, context) { lastKnownPosition(context) }
    val position = current?.point?.coordinate() ?: points.lastOrNull()?.coordinate() ?: initial
    val map = maps.filter { position != null && it.bounds.contains(position) }
        .minByOrNull { (it.bounds.maxLatitude - it.bounds.minLatitude) * (it.bounds.maxLongitude - it.bounds.minLongitude) }
    val color = MaterialTheme.colorScheme.primary.toArgb()
    val halo = MaterialTheme.colorScheme.onPrimary.toArgb()
    val language = LocalAppLanguage.current.locale.language
    var failed by remember(map) { mutableStateOf(false) }

    Box(modifier.clip(RoundedCornerShape(24.dp))) {
        if (map == null || failed || position == null) {
            RouteSketch(points, Modifier.fillMaxSize())
        } else {
            key(map, language) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        FrameLayout(viewContext).apply {
                            try {
                                val offline = OfflineRouteView(viewContext, map, position, live, language, color, halo)
                                tag = offline
                                addView(offline.view, FrameLayout.LayoutParams(-1, -1))
                            } catch (_: Exception) {
                                post { failed = true }
                            }
                        }
                    },
                    onRelease = { (it.tag as? OfflineRouteView)?.view?.destroyAll() },
                    update = { (it.tag as? OfflineRouteView)?.update(points, current) }
                )
            }
            Surface(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")))
                    } catch (_: ActivityNotFoundException) {
                        // The map remains usable offline.
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(topStart = 10.dp)
            ) {
                Text("© OpenStreetMap contributors", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private class OfflineRouteView(
    context: Context, map: OfflineMap, center: LatLong, private val live: Boolean,
    language: String, color: Int, haloColor: Int
) {
    val view: MapView
    private val line: Polyline
    private val halo: Polyline
    private val endpoint: FixedPixelCircle
    private val cursor: LocationCursor
    private var displayed: List<RoutePoint>? = null
    private var followed: RoutePoint? = null
    private var following = true

    init {
        AndroidGraphicFactory.createInstance(context.applicationContext)
        val graphics = AndroidGraphicFactory.INSTANCE
        val mapFile = MapFile(map.file, language)
        view = MapView(context)
        try {
            val density = context.resources.displayMetrics.density
            val cache = InMemoryTileCache(64)
            val renderer = TileRendererLayer(cache, mapFile, view.model.mapViewPosition, graphics)
            renderer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
            view.layerManager.layers.add(renderer)
            fun stroke(tint: Int, width: Float): org.mapsforge.core.graphics.Paint {
                val paint = graphics.createPaint()
                paint.setColor(tint)
                paint.setStyle(Style.STROKE)
                paint.setStrokeWidth(width * density)
                paint.setStrokeCap(Cap.ROUND)
                paint.setStrokeJoin(Join.ROUND)
                return paint
            }
            halo = Polyline(stroke(haloColor, 8f), graphics)
            line = Polyline(stroke(color, 5f), graphics)
            val fill = graphics.createPaint().apply { setColor(color); setStyle(Style.FILL) }
            endpoint = FixedPixelCircle(center, 5 * density, fill, stroke(haloColor, 2f))
            endpoint.setVisible(false)
            cursor = LocationCursor(graphics, density, color, haloColor)
            view.layerManager.layers.add(halo)
            view.layerManager.layers.add(line)
            view.layerManager.layers.add(endpoint)
            if (live) view.layerManager.layers.add(cursor)
            view.setBuiltInZoomControls(false)
            view.addInputListener(object : InputListener {
                override fun onMoveEvent() { following = false }
                override fun onZoomEvent() { following = false }
            })
            view.setClickable(true)
            view.setCenter(center)
            view.setZoomLevel(16)
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (!live) displayed?.let(::fit)
            }
        } catch (error: Exception) {
            view.destroyAll()
            mapFile.close()
            throw error
        }
    }

    fun update(points: List<RoutePoint>, current: MapLocation?) {
        if (points != displayed) {
            displayed = points
            val route = points.map(RoutePoint::coordinate)
            halo.setPoints(route)
            line.setPoints(route)
            endpoint.setVisible(!live && route.isNotEmpty())
            route.lastOrNull()?.let(endpoint::setLatLong)
            if (!live) fit(points)
            view.layerManager.redrawLayers()
        }
        if (live) {
            val location = current ?: points.lastOrNull()?.let { MapLocation(it, null) }
            cursor.update(location)
            if (following && location != null && location.point != followed) {
                followed = location.point
                view.setCenter(location.point.coordinate())
            }
        }
    }

    private fun fit(points: List<RoutePoint>) {
        if (points.size < 2 || view.width == 0 || view.height == 0) return
        val bounds = BoundingBox(points.map(RoutePoint::coordinate))
        val dimension = org.mapsforge.core.model.Dimension((view.width * .8).toInt(), (view.height * .8).toInt())
        val zoom = LatLongUtils.zoomForBounds(dimension, bounds, view.model.displayModel.tileSize)
        view.setCenter(bounds.centerPoint)
        view.setZoomLevel(zoom.coerceIn(2, 17))
    }
}

private class LocationCursor(
    private val graphics: AndroidGraphicFactory, private val density: Float, color: Int, outline: Int
) : Layer() {
    @Volatile private var location: MapLocation? = null
    private val fill = graphics.createPaint().apply { setColor(color); setStyle(Style.FILL) }
    private val border = graphics.createPaint().apply {
        setColor(outline)
        setStyle(Style.STROKE)
        setStrokeWidth(2.5f * density)
        setStrokeJoin(Join.ROUND)
    }

    fun update(next: MapLocation?) {
        if (location == next) return
        location = next
        requestRedraw()
    }

    override fun draw(bounds: BoundingBox, zoom: Byte, canvas: Canvas, topLeft: Point, rotation: Rotation) {
        val marker = location ?: return
        val point = marker.point.coordinate()
        if (!bounds.contains(point)) return
        val size = MercatorProjection.getMapSize(zoom, displayModel.tileSize)
        val x = (MercatorProjection.longitudeToPixelX(point.longitude, size) - topLeft.x).toFloat()
        val y = (MercatorProjection.latitudeToPixelY(point.latitude, size) - topLeft.y).toFloat()
        val heading = marker.heading
        if (heading == null) {
            val radius = (7 * density).roundToInt()
            canvas.drawCircle(x.roundToInt(), y.roundToInt(), radius, fill)
            canvas.drawCircle(x.roundToInt(), y.roundToInt(), radius, border)
            return
        }
        val angle = Math.toRadians(heading.toDouble())
        val dx = sin(angle).toFloat() * density
        val dy = -cos(angle).toFloat() * density
        val arrow = graphics.createPath()
        arrow.moveTo(x + dx * 14, y + dy * 14)
        arrow.lineTo(x - dx * 9 - dy * 9, y - dy * 9 + dx * 9)
        arrow.lineTo(x - dx * 4, y - dy * 4)
        arrow.lineTo(x - dx * 9 + dy * 9, y - dy * 9 - dx * 9)
        arrow.close()
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, border)
    }
}

private fun RoutePoint.coordinate() = LatLong(lat, lon)

@SuppressLint("MissingPermission")
private fun lastKnownPosition(context: Context): LatLong? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) return null
    val locations = context.getSystemService(LocationManager::class.java)
    return runCatching {
        locations.getProviders(true).mapNotNull { provider ->
            runCatching { locations.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.elapsedRealtimeNanos }?.let { LatLong(it.latitude, it.longitude) }
    }.getOrNull()
}

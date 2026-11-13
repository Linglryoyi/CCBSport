@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ccbsport.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun Glyph(id: Int, description: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(id), description, modifier)
}

val Sport.icon: Int get() = when (this) {
    Sport.Outdoor -> R.drawable.directions_run
    Sport.Indoor -> R.drawable.timer
    Sport.Fitness -> R.drawable.fitness_center
    Sport.Walk -> R.drawable.directions_walk
}

@Composable
fun HomeScreen(name: String, records: List<Workout>, onSport: (Sport) -> Unit, onRecord: (Workout) -> Unit, all: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val seconds = records.sumOf { it.seconds }
    val distance = records.filter { it.sport == Sport.Outdoor || it.sport == Sport.Indoor }.sumOf { it.meters }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..10 -> t(R.string.morning)
        in 11..13 -> t(R.string.noon)
        in 14..17 -> t(R.string.afternoon)
        else -> t(R.string.evening)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)) {
            Text(if (name.isBlank()) t(R.string.greet, greeting) else t(R.string.greet_name, greeting, name),
                style = type.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(t(R.string.get_moving), color = colors.onSurfaceVariant, style = type.titleMedium)
        }
        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { Sport.entries.size },
            preferredItemWidth = 248.dp, itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(180.dp)
        ) { index ->
            val sport = Sport.entries[index]
            val background = listOf(colors.primaryContainer, colors.secondaryContainer, colors.tertiaryContainer, colors.surfaceContainerHigh)[index]
            val foreground = listOf(colors.onPrimaryContainer, colors.onSecondaryContainer, colors.onTertiaryContainer, colors.onSurface)[index]
            Surface(
                onClick = { onSport(sport) }, color = background, contentColor = foreground,
                modifier = Modifier.fillMaxSize().maskClip(RoundedCornerShape(16.dp))
            ) {
                Box {
                    Surface(
                        color = foreground.copy(alpha = .09f), contentColor = foreground, shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) { Glyph(sport.icon, modifier = Modifier.size(42.dp)) }
                    }
                    Text(t(sport.label), style = type.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t(R.string.recent_workouts), Modifier.weight(1f), style = type.titleLarge)
                if (records.isNotEmpty()) TextButton(onClick = all) { Text(t(R.string.all)) }
            }
            Spacer(Modifier.height(8.dp))
            if (records.isEmpty()) {
                Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Glyph(R.drawable.history)
                        Text(t(R.string.no_recent), style = type.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            } else {
                val recent = records.take(3)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    recent.forEachIndexed { index, record ->
                        RecordRow(record, index, recent.size) { onRecord(record) }
                    }
                }
            }
            Text(t(R.string.workout_totals), Modifier.padding(top = 20.dp, bottom = 8.dp), style = type.titleLarge)
            Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val duration = if (seconds >= 3600) t(R.string.hours, seconds / 3600) else if (seconds >= 60) t(R.string.minutes, seconds / 60) else t(R.string.seconds, seconds)
                        Text(duration, Modifier.weight(1f), style = type.headlineSmall, maxLines = 1)
                        Text("${km(distance)} km", Modifier.weight(1f), style = type.headlineSmall, maxLines = 1)
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth()) {
                        Text(t(R.string.total_time), Modifier.weight(1f), color = colors.onSurfaceVariant)
                        Text(t(R.string.run_distance), Modifier.weight(1f), color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SportTabs(sport: Sport, change: (Sport) -> Unit) {
    PrimaryTabRow(selectedTabIndex = sport.ordinal) {
        Sport.entries.forEach { item ->
            Tab(selected = sport == item, onClick = { change(item) },
                text = { Text(t(item.label), maxLines = 2, textAlign = TextAlign.Center) })
        }
    }
}

@Composable
fun ExerciseScreen(
    sport: Sport, session: Session?, onSport: (Sport) -> Unit, onStart: () -> Unit, onEnd: () -> Unit, onDemo: () -> Unit
) {
    val shown = session?.sport ?: sport
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    var elapsed by remember(session?.started) { mutableLongStateOf(session?.seconds() ?: 0) }
    LaunchedEffect(session?.started) {
        while (session != null) {
            elapsed = session.seconds()
            delay(250)
        }
    }
    Column(Modifier.fillMaxSize()) {
        SportTabs(shown) { if (session == null) onSport(it) }
        if (shown == Sport.Outdoor) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                RouteMap(session?.points.orEmpty(), Modifier.fillMaxSize(), live = true)
                if (session == null) {
                    val currentDemo by rememberUpdatedState(onDemo)
                    val demoLabel = t(R.string.demo_title)
                    var longPress by remember { mutableStateOf(false) }
                    Button(
                        onClick = { if (!longPress) onStart() else longPress = false },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
                            .width(182.dp).height(56.dp)
                            .semantics { onLongClick(demoLabel) { currentDemo(); true } }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    longPress = false
                                    val ended = withTimeoutOrNull(3000L) {
                                        waitForUpOrCancellation(PointerEventPass.Initial)
                                        true
                                    }
                                    if (ended == null) {
                                        longPress = true
                                        currentDemo()
                                    }
                                }
                            }
                    ) {
                        Glyph(R.drawable.directions_run)
                        Spacer(Modifier.width(8.dp))
                        Text(t(R.string.go))
                    }
                }
            }
            if (session != null) {
                Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t(R.string.session_distance, km(session.meters)), style = type.headlineSmall)
                        val status = when {
                            session.hasRecentFix() -> t(R.string.gps_pace, pace(elapsed, session.meters))
                            session.counting -> t(R.string.step_estimate, session.steps)
                            session.tracking -> t(R.string.waiting_gps)
                            else -> t(R.string.timer_only)
                        }
                        Text(status, style = type.bodyMedium, color = colors.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(clock(elapsed), style = type.titleLarge)
                            FilledTonalButton(onClick = onEnd, modifier = Modifier.weight(1f).height(56.dp)) { Text(t(R.string.finish)) }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(t(shown.label), style = type.headlineMedium)
                Text(t(shown.hint), style = type.bodyMedium, color = colors.onSurfaceVariant)
                Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer,
                    shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (session != null) {
                            Text(clock(elapsed), style = type.displayMedium, maxLines = 1)
                            Spacer(Modifier.height(12.dp))
                            LoadingIndicator()
                            if (session.counting) {
                                Spacer(Modifier.height(12.dp))
                                Text("${km(session.meters)} km · ${t(R.string.step_estimate, session.steps)}")
                            }
                        } else {
                            Glyph(shown.icon, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(t(R.string.workout_prompt), style = type.headlineSmall)
                        }
                    }
                }
                Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (session == null) t(R.string.start_tracking) else t(R.string.stop_tracking), Modifier.weight(1f), style = type.titleMedium)
                        Switch(checked = session != null, onCheckedChange = { if (session == null) onStart() else onEnd() })
                    }
                }
            }
        }
    }
}

@Composable
fun RouteSketch(points: List<RoutePoint>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHigh)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val road = colors.surface.copy(alpha = .94f)
            val block = colors.primaryContainer.copy(alpha = .42f)
            for (x in 0..4) for (y in 0..6) {
                if ((x + y * 2) % 4 == 0) {
                    drawRoundRect(block, Offset(w * (.04f + x * .21f), h * (.018f + y * .16f)),
                        Size(w * .14f, h * .085f), CornerRadius(9.dp.toPx()))
                }
            }
            for (i in 1..5) {
                val y = h * i / 6
                drawLine(road, Offset(0f, y + h * .06f), Offset(w, y - h * .06f), 6.dp.toPx())
            }
            for (i in 0..4) {
                val x = w * i / 4
                drawLine(road, Offset(x - w * .09f, 0f), Offset(x + w * .09f, h), 8.dp.toPx())
            }
            if (points.isNotEmpty()) {
                val minX = points.minOf { it.lon }
                val minY = points.minOf { it.lat }
                val lonScale = kotlin.math.cos(Math.toRadians(points.first().lat)).coerceAtLeast(.01)
                val spanX = ((points.maxOf { it.lon } - minX) * lonScale).coerceAtLeast(.00001)
                val spanY = (points.maxOf { it.lat } - minY).coerceAtLeast(.00001)
                val scale = minOf(w * .70 / spanX, h * .55 / spanY)
                val drawn = points.map {
                    Offset((w / 2 + ((it.lon - minX) * lonScale - spanX / 2) * scale).toFloat(),
                        (h / 2 - (it.lat - minY - spanY / 2) * scale).toFloat())
                }
                val path = Path().apply {
                    moveTo(drawn.first().x, drawn.first().y)
                    drawn.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, colors.surface, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, colors.primary, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(colors.onPrimary, 9.dp.toPx(), drawn.last())
                drawCircle(colors.primary, 5.dp.toPx(), drawn.last())
            }
        }
        Surface(color = colors.surface, contentColor = colors.onSurfaceVariant, shape = CircleShape,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Text(t(R.string.route_preview), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
        if (points.isEmpty()) {
            Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer,
                shape = CircleShape, modifier = Modifier.align(Alignment.Center).size(64.dp)) {
                Box(contentAlignment = Alignment.Center) { Glyph(R.drawable.location_on, modifier = Modifier.size(36.dp)) }
            }
        }
    }
}

@Composable
fun RecordRow(record: Workout, index: Int, size: Int, click: () -> Unit) {
    val top = if (index == 0) 24.dp else 8.dp
    val bottom = if (index == size - 1) 24.dp else 8.dp
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = click, color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(top, top, bottom, bottom)
    ) {
        ListItem(
            headlineContent = { Text(t(record.sport.label)) },
            supportingContent = { Text(dateLabel(record.date) + if (record.meters > 0) " · ${km(record.meters)} km" else " · ${clock(record.seconds)}") },
            leadingContent = { Bubble(record.sport.icon) },
            trailingContent = { Glyph(R.drawable.chevron_right) },
            colors = ListItemDefaults.colors(containerColor = colors.surfaceContainerLow),
            modifier = Modifier.heightIn(min = 72.dp)
        )
    }
}

@Composable
fun Bubble(icon: Int) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Glyph(icon) }
    }
}

@Composable
fun RecordsScreen(records: List<Workout>, onRecord: (Workout) -> Unit, onExercise: () -> Unit) {
    val strings = LocalAppStrings.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val results = records.filter {
        val category = when (filter) {
            1 -> it.sport == Sport.Outdoor
            2 -> it.sport == Sport.Indoor
            3 -> it.sport == Sport.Walk || it.sport == Sport.Fitness
            else -> true
        }
        val text = "${strings.getString(it.sport.label)} ${dateLabel(it.date)} ${it.note}"
        category && text.contains(query.trim(), ignoreCase = true)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        item {
            Text(t(R.string.history_heading, records.size), style = type.headlineMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(t(R.string.all), t(R.string.outdoor), t(R.string.indoor), t(R.string.other)).forEachIndexed { index, label ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) },
                        leadingIcon = if (filter == index) {{ Glyph(R.drawable.check, modifier = Modifier.size(16.dp)) }} else null)
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.search_records)) },
                placeholder = { Text(t(R.string.search_hint)) }, leadingIcon = { Glyph(R.drawable.search) },
                singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(12.dp))
        }
        if (results.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Bubble(R.drawable.history)
                    Text(if (records.isEmpty()) t(R.string.no_workouts) else t(R.string.no_results),
                        style = type.titleMedium, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                    if (records.isEmpty()) FilledTonalButton(onClick = onExercise) { Text(t(R.string.go_exercise)) }
                }
            }
        }
        itemsIndexed(results, key = { _, record -> record.id }) { index, record ->
            RecordRow(record, index, results.size) { onRecord(record) }
        }
    }
}

@Composable
fun Field(
    value: String, change: (String) -> Unit, label: String, numeric: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = numeric))
}

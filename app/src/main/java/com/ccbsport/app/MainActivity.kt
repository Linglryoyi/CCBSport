@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ccbsport.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = Store(newBase).language()
        super.attachBaseContext(language?.context(newBase) ?: newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0, 0),
            navigationBarStyle = SystemBarStyle.light(0, 0)
        )
        setContent {
            val context = LocalContext.current
            val store = remember(context) { Store(context) }
            var theme by remember { mutableStateOf(store.theme()) }
            val dark = when (theme) {
                AppThemeMode.System -> isSystemInDarkTheme()
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            MaterialExpressiveTheme(colorScheme = if (dark) PurpleDark else Purple, motionScheme = MotionScheme.standard()) {
                val language = remember { store.language() }
                fun select(next: AppLanguage) {
                    if (next == language) return
                    store.saveLanguage(next)
                    recreate()
                }
                val current = language
                if (current == null) {
                    LanguageScreen(null, ::select)
                } else {
                    CompositionLocalProvider(LocalAppLanguage provides current, LocalAppStrings provides context) {
                        var onboarding by remember { mutableStateOf(!store.hasCompletedMapSetup()) }
                        if (onboarding) {
                            OfflineMapSetup {
                                if (store.completeMapSetup()) onboarding = false
                            }
                        } else SportApp(store, current, ::select, theme, { next ->
                            store.saveTheme(next)
                            theme = next
                        }, onReset = {
                            if (!store.clear()) false
                            else {
                                OfflineMaps.get(context).clearCities()
                                recreate()
                                true
                            }
                        })
                    }
                }
            }
        }
    }
}

private val Purple = lightColorScheme(
    primary = Color(0xFF6750A4), onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF635A75), secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B), tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D), surface = Color(0xFFFEF7FF),
    background = Color(0xFFFEF7FF), surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7), surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9), onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F), outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0), inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7), inversePrimary = Color(0xFFD0BCFF),
    error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private enum class Page(val title: Int, val label: Int, val icon: Int) {
    Home(R.string.home, R.string.home, R.drawable.home),
    Exercise(R.string.exercise, R.string.exercise, R.drawable.directions_run),
    Records(R.string.records_title, R.string.records, R.drawable.history),
    Settings(R.string.settings, R.string.settings, R.drawable.settings)
}

enum class SettingsPage(val title: Int) {
    Offline(R.string.offline_maps), Data(R.string.data_management), About(R.string.about)
}

@Composable
private fun SportApp(
    store: Store, language: AppLanguage, selectLanguage: (AppLanguage) -> Unit,
    theme: AppThemeMode, setTheme: (AppThemeMode) -> Unit, onReset: () -> Boolean
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedScreens = rememberSaveableStateHolder()
    var records by remember { mutableStateOf(store.read()) }
    var name by remember { mutableStateOf(store.name()) }
    var avatar by remember { mutableStateOf(store.avatar()) }
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var sport by rememberSaveable { mutableStateOf(Sport.Outdoor) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var generating by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var demoDialog by rememberSaveable { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var languageDialog by rememberSaveable { mutableStateOf(false) }
    var stride by remember { mutableDoubleStateOf(store.stride()) }
    var requestedSport by rememberSaveable { mutableStateOf<Sport?>(null) }
    var pending by remember { mutableStateOf<Workout?>(null) }
    val session by ActiveWorkout.current.collectAsState()
    val detail = records.firstOrNull { it.id == detailId }

    fun notice(message: String) { scope.launch { snackbars.showSnackbar(message) } }
    fun save(next: List<Workout>): Boolean {
        if (!store.write(next)) {
            notice(strings.getString(R.string.save_failed))
            return false
        }
        records = next.sortedByDescending { it.date }
        return true
    }
    fun visit(next: Page) {
        page = next
        detailId = null
        generating = false
        settingsPage = null
        if (drawer.isOpen) scope.launch { drawer.close() }
    }
    fun show(record: Workout) {
        visit(Page.Records)
        detailId = record.id
    }
    fun start(type: Sport) {
        if (ActiveWorkout.current.value != null) return
        val gps = context.getSystemService(LocationManager::class.java)
        val tracking = type == Sport.Outdoor &&
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            gps.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val counting = type != Sport.Fitness && context.canCountSteps() && context.stepSensor() != null
        ActiveWorkout.current.value = Session(type, tracking = tracking, counting = counting)
        if (tracking || counting) {
            try {
                context.startForegroundService(Intent(context, RouteService::class.java))
            } catch (_: RuntimeException) {
                ActiveWorkout.current.value = ActiveWorkout.current.value?.copy(tracking = false, counting = false)
            }
        }
        if (type != Sport.Fitness && ActiveWorkout.current.value?.let { !it.tracking && !it.counting } == true) {
            notice(strings.getString(R.string.timer_only))
        }
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        requestedSport?.let { start(it) }
        requestedSport = null
    }
    fun requestStart(type: Sport) {
        if (requestedSport != null || ActiveWorkout.current.value != null) return
        val needed = buildList {
            if (type == Sport.Outdoor && context.getSystemService(LocationManager::class.java)
                    .isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (type != Sport.Fitness && context.stepSensor() != null && !context.canCountSteps()) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (needed.isEmpty()) start(type)
        else {
            requestedSport = type
            permission.launch(needed.toTypedArray())
        }
    }
    fun end() {
        val running = ActiveWorkout.current.value ?: return
        pending = Workout(running.sport, running.date, running.seconds(), running.meters,
            points = running.points, steps = running.steps, estimatedMeters = running.estimatedMeters)
    }
    fun clearSession() {
        ActiveWorkout.current.value = null
        context.stopService(Intent(context, RouteService::class.java))
        pending = null
    }
    fun back() {
        when {
            detail != null -> detailId = null
            generating -> generating = false
            settingsPage != null -> settingsPage = null
            else -> visit(Page.Home)
        }
    }

    BackHandler(drawer.isOpen || page != Page.Home || detail != null || generating || settingsPage != null) {
        if (drawer.isOpen) scope.launch { drawer.close() } else back()
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 340.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileAvatar(avatar, Modifier.size(48.dp))
                        Column {
                            Text("CCBSport", style = MaterialTheme.typography.titleLarge)
                            Text(name.ifBlank { t(R.string.athlete) }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Page.entries.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(t(item.title)) }, icon = { Glyph(item.icon) },
                            selected = item == page, onClick = { visit(item) }
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                    NavigationDrawerItem(
                        label = { Text(t(R.string.language)) }, icon = { Glyph(R.drawable.language) },
                        badge = { Text(language.label) }, selected = false,
                        onClick = { scope.launch { drawer.close(); languageDialog = true } }
                    )
                }
            }
        }
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(t(when {
                    detail != null -> R.string.workout_details
                    else -> settingsPage?.title ?: page.title
                })) },
                navigationIcon = {
                    Box {
                        if (detail != null || generating || settingsPage != null) {
                            IconButton(onClick = { back() }) { Glyph(R.drawable.arrow_back, t(R.string.back)) }
                        } else {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Glyph(R.drawable.menu, t(R.string.menu))
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Page.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == page, onClick = { visit(item) },
                        icon = { Glyph(item.icon) }, label = { Text(t(item.label)) }
                    )
                }
            }
        }
    ) { padding ->
        val screen = when {
            detail != null -> "detail/${detail.id}"
            generating -> "generate"
            settingsPage != null -> "settings/${settingsPage?.name}"
            else -> page.name
        }
        AnimatedContent(
            targetState = screen, modifier = Modifier.fillMaxSize().padding(padding)
                .consumeWindowInsets(padding).imePadding(),
            transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(120)) }, label = "screen"
        ) { visible ->
            when (visible) {
                "Home" -> HomeScreen(name, records, onSport = {
                    sport = it
                    visit(Page.Exercise)
                }, onRecord = ::show, all = { visit(Page.Records) })
                "Exercise" -> ExerciseScreen(
                    sport, session, onSport = { sport = it },
                    onStart = { requestStart(sport) },
                    onEnd = ::end, onDemo = { demoDialog = true }
                )
                "Records" -> savedScreens.SaveableStateProvider("Records") {
                    RecordsScreen(records, onRecord = ::show, onExercise = { visit(Page.Exercise) })
                }
                "Settings" -> SettingsScreen(name, avatar, language, { languageDialog = true }, stride, { next ->
                    store.saveStride(next)
                    stride = next
                }, { settingsPage = it }, theme, setTheme) { nextName, nextAvatar ->
                    store.saveProfile(nextName, nextAvatar)
                    name = nextName
                    avatar = nextAvatar
                }
                "settings/Offline" -> OfflineMapScreen()
                "settings/About" -> AboutScreen()
                "settings/Data" -> DataManagement(records, session != null, merge = { incoming ->
                    val ids = records.mapTo(HashSet()) { it.id }
                    val added = incoming.filter { ids.add(it.id) }
                    if (added.isEmpty() || save(records + added)) added.size else null
                }, clear = {
                    if (ActiveWorkout.current.value == null) {
                        if (onReset()) visit(Page.Home) else notice(strings.getString(R.string.save_failed))
                    }
                })
                "generate" -> Column {
                    SportTabs(Sport.Outdoor) {
                        if (it != Sport.Outdoor) {
                            sport = it
                            generating = false
                        }
                    }
                    GenerateScreen { new ->
                        if (save(records + new)) {
                            visit(Page.Records)
                            notice(strings.getString(R.string.records_added, new.size))
                        }
                    }
                }
                else -> if (visible.startsWith("detail/")) {
                    records.firstOrNull { it.id == visible.substringAfter('/') }?.let { record ->
                        DetailScreen(record, update = { changed ->
                            save(records.map { if (it.id == changed.id) changed else it })
                        }, delete = {
                            if (save(records.filterNot { it.id == record.id })) detailId = null
                        })
                    }
                }
            }
        }
    }
    }

    if (languageDialog) {
        var choice by rememberSaveable(language) { mutableStateOf(language) }
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(t(R.string.language)) },
            text = { LanguageChoices(choice) { choice = it } },
            dismissButton = { TextButton(onClick = { languageDialog = false }) { Text(t(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { selectLanguage(choice); languageDialog = false }) { Text(t(R.string.save)) } }
        )
    }
    if (demoDialog) AlertDialog(
        onDismissRequest = { demoDialog = false },
        title = { Text(t(R.string.demo_title)) },
        text = { Text(t(R.string.demo_help)) },
        dismissButton = { TextButton(onClick = { demoDialog = false }) { Text(t(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = { demoDialog = false; generating = true }) { Text(t(R.string.open_generator)) } }
    )
    pending?.let { record ->
        RecordEditor(
            record = record, ending = true, dismiss = { pending = null },
            discard = { clearSession() },
            onSave = { result ->
                if (save(records + result)) {
                    clearSession()
                    show(result)
                    true
                } else false
            }
        )
    }
}

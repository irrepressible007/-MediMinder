package com.example.mediminder.ui

import com.example.mediminder.ui.account.AccountScreen
import com.example.mediminder.ui.more.LegalDoc
import com.example.mediminder.ui.more.LegalScreen
import com.example.mediminder.ui.prescription.PrescriptionScreen
import com.example.mediminder.ui.more.FamilyAlertScreen
import com.example.mediminder.ui.more.RamadanScreen
import com.example.mediminder.domain.CareKind
import com.example.mediminder.ui.health.CareEditorScreen
import com.example.mediminder.ui.health.HealthScreen
import com.example.mediminder.ui.health.ReadingsScreen
import com.example.mediminder.ui.health.RecordReadingScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.example.mediminder.ui.components.UpdateDialogHost
import com.example.mediminder.updater.Updater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import com.example.mediminder.ui.components.FeedbackHost
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.mediminder.R
import com.example.mediminder.ui.more.HistoryScreen
import com.example.mediminder.ui.components.rememberReduceMotion
import com.example.mediminder.ui.components.Motion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContent
import com.example.mediminder.ui.photos.PhotoCaptureScreen
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.ui.appointments.AppointmentEditorScreen
import com.example.mediminder.ui.appointments.AppointmentsScreen
import com.example.mediminder.ui.medicines.MedicineDetailScreen
import com.example.mediminder.ui.medicines.MedicineEditorScreen
import com.example.mediminder.ui.medicines.MedicinesScreen
import com.example.mediminder.ui.more.MedicalIdScreen
import com.example.mediminder.ui.more.MoreScreen
import com.example.mediminder.ui.more.OnboardingScreen
import com.example.mediminder.ui.more.ReliabilityScreen
import com.example.mediminder.ui.more.SettingsScreen
import com.example.mediminder.ui.more.StatsScreen
import com.example.mediminder.ui.restock.RefillListScreen
import com.example.mediminder.ui.screens.CameraScannerScreen
import com.example.mediminder.ui.screens.MedicalVaultScreen
import com.example.mediminder.ui.today.TodayScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject

// ── Destinations ─────────────────────────────────────────────────────────────
@Serializable data object Onboarding : NavKey
@Serializable data object Home : NavKey
/** medicationId 0 = new medicine */
@Serializable data class MedicineEditor(val medicationId: Long = 0) : NavKey
@Serializable data class MedicineDetail(val medicationId: Long, val openRestock: Boolean = false) : NavKey
@Serializable data object Scanner : NavKey
@Serializable data object PhotoCapture : NavKey
@Serializable data object RefillList : NavKey
/** appointmentId 0 = new; doctorId pre-fills the doctor */
@Serializable data class AppointmentEditor(val appointmentId: Long = 0, val doctorId: Long = 0) : NavKey
@Serializable data object Stats : NavKey
@Serializable data object MedicalId : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object ReliabilityKey : NavKey
@Serializable data object Vault : NavKey
/** medicationId 0 = every medicine */
@Serializable data class HistoryKey(val medicationId: Long = 0) : NavKey
@Serializable data object HealthKey : NavKey
/** [kind] is "MEASURE" or "HABIT"; medicationId 0 = a new one. */
@Serializable data class CareEditorKey(val kind: String, val medicationId: Long = 0) : NavKey
/** Write down a reading: of a known [type], for a health check, or answering one reminder (scheduleId + day). */
@Serializable data class RecordKey(val type: String = "", val medicationId: Long = 0, val scheduleId: Long = 0, val day: Long = -1) : NavKey
@Serializable data class ReadingsKey(val type: String) : NavKey
@Serializable data object RamadanKey : NavKey
@Serializable data object FamilyAlertKey : NavKey
@Serializable data object PrescriptionKey : NavKey
@Serializable data object PrescriptionPhotoKey : NavKey
/** "PRIVACY" or "TERMS" */
@Serializable data class LegalKey(val doc: String) : NavKey
@Serializable data object AccountKey : NavKey

enum class HomeTab { TODAY, MEDICINES, APPOINTMENTS, MORE }

/** Destinations a notification can open. */
sealed class DeepLink {
    data object Today : DeepLink()
    data object Refills : DeepLink()
    data object Appointments : DeepLink()
    data class Medicine(val id: Long) : DeepLink()
    data class Restock(val id: Long) : DeepLink()
    data object Reliability : DeepLink()
    /** A reminder to check: write the reading down (and mark that reminder done). */
    data class Record(val scheduleId: Long, val day: Long) : DeepLink()
    /** A health check answered from the alarm: write the reading down. */
    data class Reading(val medicationId: Long) : DeepLink()
}

@HiltViewModel
class RootViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val onboardingDone: Boolean = settings.current.onboardingDone
}

@Composable
fun MainNavigation(
    deepLink: StateFlow<DeepLink?>,
    onDeepLinkHandled: () -> Unit,
    root: RootViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(if (root.onboardingDone) Home else Onboarding)
    var tab by rememberSaveable { mutableStateOf(HomeTab.TODAY) }
    // Handed back to the medicine editor: the brand read by the scanner and/or a photo of the box
    var scannedCatalogueId by rememberSaveable { mutableStateOf<Long?>(null) }
    var capturedPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    // A photo of a prescription, handed back to the prescription reader
    var prescriptionPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val link by deepLink.collectAsState()
    LaunchedEffect(link) {
        val target = link ?: return@LaunchedEffect
        if (backStack.lastOrNull() != Onboarding) {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            when (target) {
                DeepLink.Today -> tab = HomeTab.TODAY
                DeepLink.Appointments -> tab = HomeTab.APPOINTMENTS
                DeepLink.Refills -> backStack += RefillList
                is DeepLink.Medicine -> backStack += MedicineDetail(target.id)
                is DeepLink.Restock -> backStack += MedicineDetail(target.id, openRestock = true)
                DeepLink.Reliability -> backStack += ReliabilityKey
                is DeepLink.Record -> backStack += RecordKey(scheduleId = target.scheduleId, day = target.day)
                is DeepLink.Reading -> backStack += RecordKey(medicationId = target.medicationId)
            }
        }
        onDeepLinkHandled()
    }

    fun open(key: NavKey) { backStack += key }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    // Screens slide in from the right and settle back when you return
    val reduceMotion = rememberReduceMotion()
    val forward: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
        else (slideInHorizontally(tween(Motion.MEDIUM, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(Motion.MEDIUM)))
            .togetherWith(slideOutHorizontally(tween(Motion.MEDIUM)) { -it / 8 } + fadeOut(tween(Motion.SHORT)))
    }
    val backward: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
        else (slideInHorizontally(tween(Motion.MEDIUM, easing = FastOutSlowInEasing)) { -it / 8 } + fadeIn(tween(Motion.MEDIUM)))
            .togetherWith(slideOutHorizontally(tween(Motion.MEDIUM)) { it / 3 } + fadeOut(tween(Motion.SHORT)))
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    NavDisplay(
        // On wide screens the app keeps a readable width, centred
        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        backStack = backStack,
        onBack = { back() },
        transitionSpec = { forward() },
        popTransitionSpec = { backward() },
        predictivePopTransitionSpec = { _ -> backward() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // each screen gets its own ViewModels, cleared when it leaves the back stack
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Onboarding> {
                OnboardingScreen(onOpenAccount = { open(AccountKey) }, onFinished = {
                    backStack.add(Home)
                    backStack.remove(Onboarding)
                })
            }
            entry<Home> {
                HomeScaffold(tab = tab, onTab = { tab = it }, open = ::open)
            }
            entry<MedicineEditor> { key ->
                MedicineEditorScreen(
                    medicationId = key.medicationId,
                    scannedCatalogueId = scannedCatalogueId,
                    capturedPhoto = capturedPhoto,
                    onScanConsumed = { scannedCatalogueId = null; capturedPhoto = null },
                    onScan = { open(Scanner) },
                    onScanPrescription = { back(); open(PrescriptionKey) },
                    onTakePhoto = { open(PhotoCapture) },
                    onBack = ::back,
                    onSaved = { id ->
                        back()
                        if (key.medicationId == 0L) open(MedicineDetail(id))
                    },
                )
            }
            entry<MedicineDetail> { key ->
                MedicineDetailScreen(
                    medicationId = key.medicationId,
                    openRestock = key.openRestock,
                    onBack = ::back,
                    onEdit = { open(MedicineEditor(key.medicationId)) },
                    onOpenHistory = { open(HistoryKey(key.medicationId)) },
                    onDeleted = ::back,
                )
            }
            entry<Scanner> {
                CameraScannerScreen(
                    onBack = ::back,
                    onPicked = { id, photo ->
                        scannedCatalogueId = id
                        capturedPhoto = photo
                        back()
                    },
                )
            }
            entry<PhotoCapture> {
                PhotoCaptureScreen(onBack = ::back, onCaptured = { ref ->
                    capturedPhoto = ref
                    back()
                })
            }
            entry<RefillList> { RefillListScreen(onBack = ::back, onOpenMedicine = { open(MedicineDetail(it)) }) }
            entry<AppointmentEditor> { key ->
                AppointmentEditorScreen(appointmentId = key.appointmentId, doctorId = key.doctorId, onBack = ::back)
            }
            entry<Stats> { StatsScreen(onBack = ::back) }
            entry<MedicalId> { MedicalIdScreen(onBack = ::back) }
            entry<SettingsKey> { SettingsScreen(onBack = ::back, onOpenReliability = { open(ReliabilityKey) }, onOpenRamadan = { open(RamadanKey) }) }
            entry<ReliabilityKey> { ReliabilityScreen(onBack = ::back) }
            entry<Vault> { MedicalVaultScreen(onBack = ::back) }
            entry<HistoryKey> { key -> HistoryScreen(medicationId = key.medicationId, onBack = ::back) }
            entry<HealthKey> {
                HealthScreen(
                    onBack = ::back,
                    onRecord = { open(RecordKey(type = it)) },
                    onOpenReadings = { open(ReadingsKey(it)) },
                    onAdd = { open(CareEditorKey(it.name)) },
                    onEdit = { id -> open(CareEditorKey(CareKind.MEASURE.name, id)) },
                )
            }
            entry<CareEditorKey> { key ->
                CareEditorScreen(kind = CareKind.valueOf(key.kind), medicationId = key.medicationId, onBack = ::back)
            }
            entry<RecordKey> { key ->
                RecordReadingScreen(type = key.type, medicationId = key.medicationId, scheduleId = key.scheduleId, day = key.day, onBack = ::back)
            }
            entry<RamadanKey> {
                RamadanScreen(
                    onBack = ::back,
                    onOpenItem = { kind, id -> open(if (kind.isMedicine) MedicineDetail(id) else CareEditorKey(kind.name, id)) },
                )
            }
            entry<FamilyAlertKey> { FamilyAlertScreen(onBack = ::back) }
            entry<LegalKey> { key -> LegalScreen(doc = LegalDoc.valueOf(key.doc), onBack = ::back) }
            entry<AccountKey> {
                AccountScreen(
                    onBack = ::back,
                    onOpenLegal = { open(LegalKey(it)) },
                    // the data came back (perhaps on a fresh install): start at Today
                    onRestored = { backStack.clear(); backStack.add(Home); tab = HomeTab.TODAY },
                )
            }
            entry<PrescriptionKey> {
                PrescriptionScreen(
                    capturedPhoto = prescriptionPhoto,
                    onPhotoConsumed = { prescriptionPhoto = null },
                    onTakePhoto = { open(PrescriptionPhotoKey) },
                    // a medicine without a dose: the wizard, starting from it
                    onSetUp = { id -> scannedCatalogueId = id; open(MedicineEditor()) },
                    onBack = ::back,
                    onDone = { back(); tab = HomeTab.MEDICINES },
                )
            }
            entry<PrescriptionPhotoKey> {
                PhotoCaptureScreen(onBack = ::back, onCaptured = { ref ->
                    prescriptionPhoto = ref
                    back()
                })
            }
            entry<ReadingsKey> { key ->
                ReadingsScreen(type = key.type, onBack = ::back, onRecord = { open(RecordKey(type = it)) })
            }
        },
    )
    // Below the top bar, so the banner never covers Back or Cancel (even with very large text)
    FeedbackHost(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 68.dp, start = 16.dp, end = 16.dp))
    UpdateDialogHost()
    }
}

@Composable
private fun HomeScaffold(tab: HomeTab, onTab: (HomeTab) -> Unit, open: (NavKey) -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val appContext = LocalContext.current.applicationContext
    // A newer version on GitHub is recommended automatically (at most every 12 hours)
    LaunchedEffect(Unit) { Updater.checkOnLaunch(appContext) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Phones: tabs along the bottom. Wide screens (tablets, foldables, landscape): a side rail.
        val wide = maxWidth >= 600.dp
        Scaffold(bottomBar = { if (!wide) BottomTabs(tab, onTab) }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) SideTabs(tab, onTab)
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    transitionSpec = {
                        if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                        else {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(tween(Motion.MEDIUM, easing = FastOutSlowInEasing)) { dir * it / 6 } + fadeIn(tween(Motion.MEDIUM)))
                                .togetherWith(slideOutHorizontally(tween(Motion.SHORT)) { -dir * it / 10 } + fadeOut(tween(Motion.SHORT)))
                        }
                    },
                    label = "tabs",
                ) { tab ->
                    val modifier = Modifier.fillMaxSize()
                    when (tab) {
                        HomeTab.TODAY -> TodayScreen(
                            modifier = modifier,
                            onAddMedicine = { open(MedicineEditor()) },
                            onOpenMedicine = { open(MedicineDetail(it)) },
                            onOpenRefills = { open(RefillList) },
                            onOpenReliability = { open(ReliabilityKey) },
                            onOpenAppointments = { onTab(HomeTab.APPOINTMENTS) },
                            onRecord = { scheduleId, day -> open(RecordKey(scheduleId = scheduleId, day = day)) },
                            onOpenCare = { kind, id -> open(CareEditorKey(kind.name, id)) },
                            onOpenRamadan = { open(RamadanKey) },
                        )
                        HomeTab.MEDICINES -> MedicinesScreen(
                            modifier = modifier,
                            onAdd = { open(MedicineEditor()) },
                            onOpen = { open(MedicineDetail(it)) },
                            onOpenRefills = { open(RefillList) },
                        )
                        HomeTab.APPOINTMENTS -> AppointmentsScreen(
                            modifier = modifier,
                            onNew = { doctorId -> open(AppointmentEditor(doctorId = doctorId)) },
                            onEdit = { open(AppointmentEditor(appointmentId = it)) },
                        )
                        HomeTab.MORE -> MoreScreen(
                            modifier = modifier,
                            onOpen = { open(it) },
                        )
                    }
                }
            }
        }
    }
}

private class TabSpec(val icon: ImageVector, @StringRes val label: Int)

private fun tabSpec(t: HomeTab) = when (t) {
    HomeTab.TODAY -> TabSpec(Icons.Default.Today, R.string.tab_today)
    HomeTab.MEDICINES -> TabSpec(Icons.Default.Medication, R.string.tab_medicines)
    HomeTab.APPOINTMENTS -> TabSpec(Icons.Default.CalendarMonth, R.string.tab_appointments)
    HomeTab.MORE -> TabSpec(Icons.Default.MoreHoriz, R.string.tab_more)
}

/** The chosen tab's icon gives a little hop. */
@Composable
private fun TabIcon(icon: ImageVector, selected: Boolean) {
    val hop by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "tabIcon",
    )
    Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = hop; scaleY = hop })
}

/** Tab names stop growing at 1.15x: they are short words under big icons, and must always fit. */
@Composable
private fun TabLabel(@StringRes label: Int) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.15f))) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BottomTabs(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        HomeTab.entries.forEach { t ->
            val spec = tabSpec(t)
            NavigationBarItem(
                selected = tab == t,
                onClick = { onTab(t) },
                icon = { TabIcon(spec.icon, tab == t) },
                label = { TabLabel(spec.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun SideTabs(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0)) {
        Spacer(Modifier.weight(1f))
        HomeTab.entries.forEach { t ->
            val spec = tabSpec(t)
            NavigationRailItem(
                selected = tab == t,
                onClick = { onTab(t) },
                icon = { TabIcon(spec.icon, tab == t) },
                label = { TabLabel(spec.label) },
                modifier = Modifier.padding(vertical = 6.dp),
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

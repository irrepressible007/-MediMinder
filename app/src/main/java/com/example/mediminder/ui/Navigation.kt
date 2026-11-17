package com.example.mediminder.ui

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

enum class HomeTab { TODAY, MEDICINES, APPOINTMENTS, MORE }

/** Destinations a notification can open. */
sealed class DeepLink {
    data object Today : DeepLink()
    data object Refills : DeepLink()
    data object Appointments : DeepLink()
    data class Medicine(val id: Long) : DeepLink()
    data class Restock(val id: Long) : DeepLink()
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

    NavDisplay(
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
                OnboardingScreen(onFinished = {
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
            entry<SettingsKey> { SettingsScreen(onBack = ::back, onOpenReliability = { open(ReliabilityKey) }) }
            entry<ReliabilityKey> { ReliabilityScreen(onBack = ::back) }
            entry<Vault> { MedicalVaultScreen(onBack = ::back) }
            entry<HistoryKey> { key -> HistoryScreen(medicationId = key.medicationId, onBack = ::back) }
        },
    )
}

@Composable
private fun HomeScaffold(tab: HomeTab, onTab: (HomeTab) -> Unit, open: (NavKey) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                HomeTab.entries.forEach { t ->
                    val (icon, label) = when (t) {
                        HomeTab.TODAY -> Icons.Default.Today to R.string.tab_today
                        HomeTab.MEDICINES -> Icons.Default.Medication to R.string.tab_medicines
                        HomeTab.APPOINTMENTS -> Icons.Default.CalendarMonth to R.string.tab_appointments
                        HomeTab.MORE -> Icons.Default.MoreHoriz to R.string.tab_more
                    }
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTab(t) },
                        icon = {
                            val hop by animateFloatAsState(
                                if (tab == t) 1.12f else 1f,
                                spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
                                label = "tabIcon",
                            )
                            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = hop; scaleY = hop })
                        },
                        label = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) },
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
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(Motion.MEDIUM)) + scaleIn(tween(Motion.MEDIUM), initialScale = 0.985f))
                    .togetherWith(fadeOut(tween(Motion.SHORT)))
            },
            label = "tabs",
        ) { tab ->
        when (tab) {
            HomeTab.TODAY -> TodayScreen(
                modifier = modifier,
                onAddMedicine = { open(MedicineEditor()) },
                onOpenMedicine = { open(MedicineDetail(it)) },
                onOpenRefills = { open(RefillList) },
                onOpenReliability = { open(ReliabilityKey) },
                onOpenAppointments = { onTab(HomeTab.APPOINTMENTS) },
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

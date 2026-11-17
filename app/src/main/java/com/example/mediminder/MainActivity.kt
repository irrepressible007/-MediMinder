package com.example.mediminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.photos.MedicinePhotos
import kotlinx.coroutines.flow.first
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.reminders.Notifier
import com.example.mediminder.reminders.ReminderEngine
import com.example.mediminder.ui.DeepLink
import com.example.mediminder.ui.MainNavigation
import com.example.mediminder.ui.theme.MediMinderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var engine: ReminderEngine
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var medicines: MedicineRepository

    /** Where a tapped notification wants to go. */
    private val deepLink = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Follow a notification's link only on a real tap: when Android rebuilds the app (after an
        // update or from Recents) it hands back the original launch, which must not reopen it
        val relaunch = savedInstanceState != null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        deepLink.value = if (relaunch) null else parse(intent)

        // Opening the app is a good moment to repair any alarm the phone dropped
        lifecycleScope.launch { engine.rescheduleAll() }
        // ...and to tidy away medicine photos that no medicine uses any more
        lifecycleScope.launch {
            val inUse = medicines.observeAll().first().mapNotNull { it.medication.photoUri }.toSet()
            MedicinePhotos.removeUnused(this@MainActivity, inUse)
        }

        // AppCompat's setContentView skips the NavigationEvent owner that Navigation3 needs
        initializeViewTreeOwners()
        setContent {
            val textScale by settings.settings.map { it.textScale }.collectAsState(initial = settings.current.textScale)
            MediMinderTheme(textScale = textScale) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation(deepLink = deepLink, onDeepLinkHandled = { deepLink.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parse(intent)?.let { deepLink.value = it }
    }

    private fun parse(intent: Intent?): DeepLink? {
        val uri = intent?.data?.toString() ?: return null
        return when {
            uri == Notifier.DEEP_LINK_TODAY -> DeepLink.Today
            uri == Notifier.DEEP_LINK_REFILLS -> DeepLink.Refills
            uri == Notifier.DEEP_LINK_APPOINTMENTS -> DeepLink.Appointments
            uri.startsWith(Notifier.DEEP_LINK_MEDICINE) -> uri.removePrefix(Notifier.DEEP_LINK_MEDICINE).toLongOrNull()?.let(DeepLink::Medicine)
            uri.startsWith(Notifier.DEEP_LINK_RESTOCK) -> uri.removePrefix(Notifier.DEEP_LINK_RESTOCK).toLongOrNull()?.let(DeepLink::Restock)
            else -> null
        }
    }
}

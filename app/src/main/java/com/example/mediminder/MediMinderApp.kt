package com.example.mediminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.os.UserManagerCompat
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.mediminder.cloud.CloudConfig
import com.example.mediminder.cloud.CloudBackupWorker
import androidx.room.InvalidationTracker
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.widget.NextDoseWidget
import com.example.mediminder.util.ErrorLog
import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.reminders.Notifier
import com.example.mediminder.reminders.SafetyNetWorker
import com.example.mediminder.util.AppLocale
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MediMinderApp : Application() {
    // Lazy: the app can start before the phone's first unlock (a dose alarm after a restart),
    // when its database and settings can't be read yet; nothing is created until needed.
    @Inject lateinit var settingsRef: Lazy<SettingsRepository>
    @Inject lateinit var notifierRef: Lazy<Notifier>
    @Inject lateinit var dbRef: Lazy<MediMinderDatabase>
    private val settings get() = settingsRef.get()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (UserManagerCompat.isUserUnlocked(this)) {
            startUp()
        } else {
            // started for a before-unlock alarm: the rest waits for the unlock
            ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    runCatching { unregisterReceiver(this) }
                    startUp()
                }
            }, IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    private fun startUp() {
        ErrorLog.init(this)
        CloudConfig.init(this)
        syncLanguage()
        notifierRef.get().ensureChannels()
        SafetyNetWorker.ensureScheduled(this)
        // The home-screen widget redraws whenever a dose is answered or a medicine changes
        dbRef.get().invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("dose_logs", "medications", "schedules", "readings", "appointments", "doctors", "restocks")) {
            override fun onInvalidated(tables: Set<String>) {
                NextDoseWidget.refresh(this@MediMinderApp)
                // signed in: the cloud copy follows a little later
                CloudBackupWorker.soon(this@MediMinderApp)
            }
        })
        // Settings (meal times, Medical ID, Ramadan…) are part of the cloud copy too. The time of
        // the last backup is left out, or each backup would start the next one.
        appScope.launch {
            settings.settings.map { it.copy(lastBackupAt = 0) }.distinctUntilChanged().drop(1).collect {
                CloudBackupWorker.soon(this@MediMinderApp)
            }
        }
    }

    /**
     * On Android 13+ the system remembers the per-app language (and the user can change it
     * in system settings), so the app follows it. Older versions use the saved setting.
     */
    private fun syncLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val system = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-')
            if (system != settings.current.language) settings.update { it.copy(language = system) }
        } else {
            AppLocale.apply(settings.current.language)
        }
    }
}

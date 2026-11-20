package com.example.mediminder

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
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
        syncLanguage()
        notifier.ensureChannels()
        SafetyNetWorker.ensureScheduled(this)
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

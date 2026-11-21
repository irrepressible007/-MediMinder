package com.example.mediminder.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** One thing that can stop reminders from ringing on time, and how to fix it. */
enum class ReliabilityCheck { NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN, BATTERY, ALARM_CHANNEL, AUTOSTART }

data class CheckResult(
    val check: ReliabilityCheck,
    /** null = cannot be detected (OEM autostart), the user has to confirm it themselves */
    val ok: Boolean?,
    val fix: Intent?,
)

/**
 * Bangladesh's most common phones (Xiaomi/Redmi/POCO, Samsung, Realme, Oppo, Vivo, Infinix,
 * Tecno) ship aggressive battery savers that kill alarms. This checks what can be checked
 * and opens the right settings page for the rest.
 */
object Reliability {
    fun checks(context: Context): List<CheckResult> {
        val packageUri = Uri.parse("package:${context.packageName}")
        val list = mutableListOf<CheckResult>()

        list += CheckResult(
            ReliabilityCheck.NOTIFICATIONS,
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
            appNotificationSettings(context),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            list += CheckResult(
                ReliabilityCheck.EXACT_ALARMS,
                alarmManager.canScheduleExactAlarms(),
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri),
            )
        }

        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(NotificationManager::class.java)
            list += CheckResult(
                ReliabilityCheck.FULL_SCREEN,
                nm.canUseFullScreenIntent(),
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri),
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = nm.getNotificationChannel(Notifier.CHANNEL_ALARMS)
            list += CheckResult(
                ReliabilityCheck.ALARM_CHANNEL,
                channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH,
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_ALARMS),
            )
        }

        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        list += CheckResult(
            ReliabilityCheck.BATTERY,
            power.isIgnoringBatteryOptimizations(context.packageName),
            batteryOptimisationIntent(context),
        )

        autostartIntent(context)?.let { list += CheckResult(ReliabilityCheck.AUTOSTART, null, it) }
        return list
    }

    fun allCriticalOk(context: Context): Boolean = criticalProblems(context).isEmpty()

    /** What still stops reminders, in the order worth fixing (without notifications nothing shows). */
    fun criticalProblems(context: Context): List<CheckResult> =
        checks(context).filter { it.check in CRITICAL && it.ok == false }.sortedBy { CRITICAL.indexOf(it.check) }

    private val CRITICAL = listOf(
        ReliabilityCheck.NOTIFICATIONS, ReliabilityCheck.EXACT_ALARMS, ReliabilityCheck.ALARM_CHANNEL, ReliabilityCheck.FULL_SCREEN,
    )

    /** Opens a settings page; if this phone does not have it, the app's own details page. */
    fun open(context: Context, intent: Intent?) {
        val target = intent ?: appDetails(context)
        try {
            context.startActivity(target)
        } catch (_: android.content.ActivityNotFoundException) {
            runCatching { context.startActivity(appDetails(context)) }
        } catch (_: SecurityException) {
            runCatching { context.startActivity(appDetails(context)) }
        }
    }

    fun appNotificationSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else appDetails(context)

    @SuppressLint("BatteryLife") // a medicine reminder must ring on time; the user confirms in a system dialog
    private fun batteryOptimisationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Manufacturer name for the guidance text, e.g. "Xiaomi". */
    val manufacturer: String get() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /**
     * The "autostart / background app" page on phones that have one. Tries the known
     * screens in order; falls back to the app's own settings page.
     */
    fun autostartIntent(context: Context): Intent? {
        val brand = Build.MANUFACTURER.lowercase()
        val candidates = when {
            brand in setOf("xiaomi", "redmi", "poco") -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            brand in setOf("oppo", "realme", "oneplus") -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
            )
            brand == "vivo" || brand == "iqoo" -> listOf(
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            )
            brand == "huawei" || brand == "honor" -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            brand == "samsung" -> listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            )
            brand in setOf("infinix", "tecno", "itel") -> listOf(
                ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
            )
            else -> return null
        }
        val pm = context.packageManager
        return candidates
            .map { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .firstOrNull { it.resolveActivity(pm) != null }
            ?: appDetails(context)
    }
}

/** In plain words: what goes wrong while this setting is off. */
@androidx.annotation.StringRes
fun ReliabilityCheck.whyItMatters(): Int = when (this) {
    ReliabilityCheck.NOTIFICATIONS -> com.example.mediminder.R.string.setup_why_notifications
    ReliabilityCheck.EXACT_ALARMS -> com.example.mediminder.R.string.setup_why_exact
    ReliabilityCheck.FULL_SCREEN -> com.example.mediminder.R.string.setup_why_full_screen
    ReliabilityCheck.ALARM_CHANNEL -> com.example.mediminder.R.string.setup_why_channel
    ReliabilityCheck.BATTERY -> com.example.mediminder.R.string.setup_why_battery
    ReliabilityCheck.AUTOSTART -> com.example.mediminder.R.string.setup_why_battery
}

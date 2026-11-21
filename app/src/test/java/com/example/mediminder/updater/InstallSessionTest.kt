package com.example.mediminder.updater

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Updates are installed by the app through PackageInstaller, not handed to Android's installer
 * app: on a Pixel 6 the installer app switched the full-screen alarm permission off each time.
 */
@RunWith(AndroidJUnit4::class)
class InstallSessionTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val info = UpdateInfo("9.9.9", 999, "https://example.invalid/x.apk", 4, null, emptyList())
    private lateinit var apk: File

    @Before fun setUp() {
        apk = File(app.cacheDir, "test.apk").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        Updater.readyForTest(info, apk)
    }

    @Test fun `updates go into an install session for MediMinder itself`() {
        // (called directly: Updater.install runs this on a worker thread)
        Updater.commitSession(app, apk)
        val session = app.packageManager.packageInstaller.allSessions.singleOrNull()
        assertNotNull("no install session", session)
        assertEquals(app.packageName, session!!.appPackageName)
        // Android's installer app was not opened with the file
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun `a refused update shows as failed`() {
        InstallResultReceiver().onReceive(app, result(PackageInstaller.STATUS_FAILURE_CONFLICT).putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "conflict"))
        val state = Updater.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(R.string.update_install_failed, (state as UpdateState.Failed).reason)
    }

    @Test fun `cancelling Android's confirmation keeps the update ready`() {
        InstallResultReceiver().onReceive(app, result(PackageInstaller.STATUS_FAILURE_ABORTED))
        assertTrue(Updater.state.value is UpdateState.Ready)
    }

    @Test fun `the confirmation opens only when it belongs to a system app`() {
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL").setPackage("com.not.system")
        registerActivity(confirm, "com.not.system", system = false)
        InstallResultReceiver().onReceive(app, result(PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, confirm))
        assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(Updater.state.value is UpdateState.Failed)
    }

    @Test fun `Android's own confirmation is opened`() {
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL").setPackage("com.android.packageinstaller")
        registerActivity(confirm, "com.android.packageinstaller", system = true)
        InstallResultReceiver().onReceive(app, result(PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, confirm))
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", shadowOf(app).nextStartedActivity?.action)
    }

    private fun result(status: Int) = Intent().putExtra(PackageInstaller.EXTRA_STATUS, status)

    private fun registerActivity(intent: Intent, pkg: String, system: Boolean) {
        val appInfo = ApplicationInfo().apply { packageName = pkg; if (system) flags = ApplicationInfo.FLAG_SYSTEM }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = appInfo })
        shadowOf(app.packageManager).addResolveInfoForIntent(intent, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Confirm"; applicationInfo = appInfo }
        })
    }
}

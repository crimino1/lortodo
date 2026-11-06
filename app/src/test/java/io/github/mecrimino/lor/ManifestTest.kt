package io.github.mecrimino.lor

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app's core promise, enforced against the merged manifest so a new dependency can't quietly break it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class ManifestTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val packageInfo
        get() = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

    @Test fun noNetworkPermissions() {
        val permissions = packageInfo.requestedPermissions.orEmpty().toList()
        assertTrue("manifest not loaded", "android.permission.SCHEDULE_EXACT_ALARM" in permissions)
        listOf("INTERNET", "ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE").forEach {
            assertFalse(it, "android.permission.$it" in permissions)
        }
    }

    @Test fun cloudBackupIsOff() {
        assertFalse(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }
}

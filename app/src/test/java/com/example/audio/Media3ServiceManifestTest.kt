package com.example.audio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Media3ServiceManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `media service is exported discoverable and declares foreground requirements`() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
        assertTrue(Manifest.permission.WAKE_LOCK in permissions)

        val service = context.packageManager.getServiceInfo(
            ComponentName(context, MediaPlaybackService::class.java),
            0
        )
        assertTrue(service.exported)
        val discovered = context.packageManager.queryIntentServices(
            Intent(MediaSessionService.SERVICE_INTERFACE).setPackage(context.packageName),
            0
        )
        assertTrue(discovered.any { it.serviceInfo.name == MediaPlaybackService::class.java.name })
    }
}

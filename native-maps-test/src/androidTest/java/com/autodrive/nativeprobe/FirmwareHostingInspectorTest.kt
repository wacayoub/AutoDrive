package com.autodrive.nativeprobe

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirmwareHostingInspectorTest {
    @Test fun publicAuditReportsRealPlatformAndDoesNotGrantPrivileges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val before = FirmwareHostingInspector.CAPABILITIES.associateWith { pm.checkPermission(it, context.packageName) }
        val audit = FirmwareHostingInspector(pm, context.packageName).inspect()
        assertEquals("COMPLETE", audit.getString("phase"))
        assertFalse(audit.getBoolean("nativeHostingVerified"))
        val packages = audit.getJSONArray("packages")
        val platform = (0 until packages.length()).map { packages.getJSONObject(it) }.single { it.getString("package") == "android" }
        assertEquals("READ", platform.getString("status"))
        assertTrue(platform.getJSONArray("currentSignerSha256").length() > 0)
        assertTrue(platform.getBoolean("platformSignatureMatch"))
        val self = (0 until packages.length()).map { packages.getJSONObject(it) }.single { it.getString("package") == context.packageName }
        assertEquals("READ", self.getString("status"))
        assertEquals(pm.checkSignatures("android", context.packageName) == PackageManager.SIGNATURE_MATCH, self.getBoolean("platformSignatureMatch"))
        assertEquals(before, FirmwareHostingInspector.CAPABILITIES.associateWith { pm.checkPermission(it, context.packageName) })
        val requested = pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())).requestedPermissions.orEmpty().toSet()
        assertTrue(requested.intersect(FirmwareHostingInspector.CAPABILITIES.toSet()).isEmpty())
        // The probe must remain an ordinary app with no HOME role or privileged manifest permissions.
        assertEquals(0, pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME).setPackage(context.packageName), PackageManager.ResolveInfoFlags.of(0)).size)
    }
}

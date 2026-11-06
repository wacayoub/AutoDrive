package com.autodrive.launcher

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keeps the normal app-private WebView profile. Never reads or exports credentials/cookies. */
internal object WebSessionStorage {
    private val disk = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + disk)

    fun configure(web: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Google sites span google.com / youtube.com. This does not bypass provider sign-in policy.
            setAcceptThirdPartyCookies(web, true)
        }
    }

    // flush() may perform disk I/O. Keep it off the UI thread and independent of Activity teardown.
    fun persist() = scope.launch {
        try { CookieManager.getInstance().flush() }
        catch (error: RuntimeException) {
            Log.w("AutoDriveWebSession", "Cookie persistence failed: ${error.javaClass.simpleName}")
        }
    }
}

package com.autodrive.incarlinkstart

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class StartActivity : Activity() {
    private lateinit var status: TextView
    private var attempted = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        status = TextView(this).apply {
            setText(R.string.opening)
            setTextColor(0xFFF5F7FA.toInt())
            textSize = 21f
        }
        panel.addView(status)
        panel.addView(TextView(this).apply {
            setText(R.string.instructions)
            setTextColor(0xFFAAB4C0.toInt())
            textSize = 16f
            setPadding(0, padding, 0, padding)
        })
        panel.addView(Button(this).apply {
            setText(R.string.retry)
            setOnClickListener { openConnectionApp() }
        })
        panel.addView(Button(this).apply {
            setText(R.string.close)
            setOnClickListener { finish() }
        })
        setContentView(panel)
        attempted = state?.getBoolean("attempted") ?: false
        state?.getCharSequence("status")?.let { status.text = it }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !attempted) openConnectionApp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        attempted = false
        if (hasWindowFocus()) openConnectionApp()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("attempted", attempted)
        outState.putCharSequence("status", status.text)
        super.onSaveInstanceState(outState)
    }

    private fun openConnectionApp() {
        attempted = true
        val target = packageManager.getLaunchIntentForPackage(CONNECTION_PACKAGE)
            ?: Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(CONNECTION_PACKAGE, CONNECTION_ACTIVITY))
        val info = packageManager.resolveActivity(target, PackageManager.ResolveInfoFlags.of(0))?.activityInfo
        if (info == null) {
            status.setText(R.string.missing)
            return
        }
        if (info.packageName != CONNECTION_PACKAGE || !info.exported ||
            !info.enabled || !info.applicationInfo.enabled) {
            status.setText(R.string.unavailable)
            return
        }
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        } catch (_: ActivityNotFoundException) {
            status.setText(R.string.unavailable)
        } catch (_: SecurityException) {
            status.setText(R.string.denied)
        }
    }

    private companion object {
        // Public launcher component inspected in the manufacturer's X15-SLM927 family image.
        const val CONNECTION_PACKAGE = "com.suding.speedplay"
        const val CONNECTION_ACTIVITY = "com.suding.speedplay.ui.MainActivity"
    }
}

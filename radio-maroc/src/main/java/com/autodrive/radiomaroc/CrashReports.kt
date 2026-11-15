package com.autodrive.radiomaroc

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime

/**
 * Keeps the cause of a crash so the listener can send it. A crash that only happens on some phones cannot be
 * reproduced on the emulator; with this report it can be fixed precisely.
 */
internal class RadioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
    }
}

internal object CrashReport {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val file = File(context.filesDir, FILE)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file.writeText(buildString {
                    appendLine("Radio Maroc & Coran $version")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("${ZonedDateTime.now()} · thread ${thread.name}")
                    appendLine()
                    append(trace.take(12_000))
                })
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): Boolean = File(context.filesDir, FILE).isFile

    fun read(context: Context): String = runCatching { File(context.filesDir, FILE).readText() }.getOrDefault("")

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}

/**
 * Opens instead of the radio after a crash: a plain screen, built without the radio's own code, that shows what
 * happened, shares the report and starts the radio again.
 */
internal class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReport.read(this)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun text(value: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 18, 21))
            setPadding(dp(20), dp(32), dp(20), dp(20))
            addView(text("La radio s’est fermée", 22f, bold = true))
            addView(text("Une erreur a arrêté l’application. Envoie le rapport ci-dessous pour qu’elle soit corrigée, " +
                "puis relance la radio.", 15f, color = Color.rgb(200, 204, 208)).apply { setPadding(0, dp(8), 0, dp(12)) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(button("Partager le rapport") {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Rapport d’erreur · Radio Maroc & Coran")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }, "Partager le rapport"))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                addView(button("Relancer la radio") {
                    CrashReport.clear(this@CrashReportActivity)
                    startActivity(Intent(this@CrashReportActivity, RadioActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                }, LinearLayout.LayoutParams(0, -2, 1f))
            })
            addView(ScrollView(context).apply {
                addView(text(report, 11f, color = Color.rgb(170, 175, 180)).apply {
                    typeface = Typeface.MONOSPACE
                    setTextIsSelectable(true)
                })
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        })
    }
}

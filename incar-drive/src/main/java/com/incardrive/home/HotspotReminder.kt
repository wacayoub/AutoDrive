package com.incardrive.home

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.content.res.ColorStateList

/**
 * Android lets only the head unit's own system apps switch the Wi-Fi hotspot on. So when the car starts (the head
 * unit booting, or waking from its sleep with the contact off), Incar Drive shows a « Point d'accès » bubble for a
 * minute: one tap opens Android's hotspot screen, where the switch is. Off in Réglages › Point d'accès.
 */
internal object HotspotReminder {
    private const val PREFS = "hotspot_reminder"
    private const val ENABLED = "enabled"
    private const val PENDING = "pending"
    private const val DONE = "done"
    private const val SHOWN_MS = 60_000L

    /** The home screen, told when the car starts while it is open. */
    var changed: (() -> Unit)? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(ENABLED, enabled).apply()

    /** The car has started; [key] names that start, so it is offered once. */
    fun driveStarted(context: Context, key: String) {
        val prefs = prefs(context)
        if (prefs.getString(DONE, null) == key || prefs.getString(PENDING, null) == key) return
        prefs.edit().putString(PENDING, key).apply()
        changed?.invoke()
    }

    fun due(context: Context): Boolean {
        val prefs = prefs(context)
        val pending = prefs.getString(PENDING, null) ?: return false
        return enabled(context) && pending != prefs.getString(DONE, null)
    }

    /** The hotspot is already on: nothing to offer for this start. */
    fun skip(context: Context) = done(context)

    private fun done(context: Context) {
        val prefs = prefs(context)
        prefs.edit().putString(DONE, prefs.getString(PENDING, null)).apply()
    }

    /** Android's « Point d'accès et partage de connexion » screen, or its network screen when the box hides it. */
    fun settingsIntent(context: Context): Intent {
        val tether = Intent("android.settings.TETHER_SETTINGS")
        return if (tether.resolveActivity(context.packageManager) != null) tether else Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }

    private val main = Handler(Looper.getMainLooper())
    private var bubble: View? = null
    private val timeout = Runnable { bubble?.let { hide(it.context, it) } }

    /** Shows the bubble over the bottom of the home screen, above the dock, when a start has not been offered yet. */
    fun show(home: HomeActivity, host: FrameLayout) {
        if (!due(home) || bubble?.parent != null) return
        val view = home.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = home.pressable(GradientDrawable().apply {
                cornerRadius = home.dp(24).toFloat()
                setColor(home.accent)
            }, 24)
            elevation = home.dp(10).toFloat()
            setPadding(home.dp(16), 0, home.dp(8), 0)
            addView(ImageView(home).apply {
                setImageResource(R.drawable.ic_home_hotspot)
                imageTintList = ColorStateList.valueOf(home.onAccent)
            }, android.widget.LinearLayout.LayoutParams(home.dp(20), home.dp(20)))
            addView(home.label("Allumer le point d’accès", 15, home.onAccent, 700),
                android.widget.LinearLayout.LayoutParams(-2, -2).apply { marginStart = home.dp(10) })
            addView(home.label("✕", 15, home.onAccent, 700).apply {
                gravity = Gravity.CENTER
                contentDescription = "Fermer"
                setOnClickListener { hide(home, this@apply.parent as View) }
            }, android.widget.LinearLayout.LayoutParams(home.dp(40), home.dp(40)).apply { marginStart = home.dp(8) })
            contentDescription = "Ouvrir le point d’accès Wi-Fi"
            setOnClickListener {
                hide(home, this)
                home.startSafely(settingsIntent(home))
            }
        }
        bubble = view
        host.addView(view, FrameLayout.LayoutParams(-2, home.dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = home.dp(72)
        })
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(220).start()
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, SHOWN_MS)
    }

    private fun hide(context: Context, view: View) {
        main.removeCallbacks(timeout)
        done(context)
        bubble = null
        view.animate().alpha(0f).setDuration(160).withEndAction { (view.parent as? FrameLayout)?.removeView(view) }.start()
    }

    /** The home screen is gone: the bubble goes with it, and is offered again next time it opens. */
    fun detach() {
        main.removeCallbacks(timeout)
        bubble = null
    }
}

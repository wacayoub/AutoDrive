package com.incardrive.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Android widgets on the second home page, through the public AppWidgetHost API: Incar Drive lists the installed
 * widgets itself, Android asks the driver once for permission to place them, and a widget with a set-up screen
 * shows it before it is added. Up to three widgets sit side by side.
 */
internal class WidgetBoard(private val home: HomeActivity) {
    private val manager = AppWidgetManager.getInstance(home)
    private val host = AppWidgetHost(home, HOST_ID)
    private var pending: Int? = null
    private var editing = false
    private lateinit var area: LinearLayout

    fun start() = runCatching { host.startListening() }
    fun stop() = runCatching { host.stopListening() }

    private fun ids(): List<Int> = home.prefs.getString(KEY, null).orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        .filter { manager.getAppWidgetInfo(it) != null }

    private fun save(ids: List<Int>) = home.prefs.edit().putString(KEY, ids.joinToString(",")).apply()

    fun view(): View = with(home) {
        column().apply {
            background = glass()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(row().apply {
                addView(caps("Widgets"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
                addView(label("Ajouter", 12, muted, 600).apply {
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    setOnClickListener { showPicker() }
                })
                addView(label("Modifier", 12, muted, 600).apply {
                    tag = "edit"
                    setPadding(dp(8), dp(4), dp(6), dp(4))
                    setOnClickListener { editing = !editing; text = if (editing) "Terminé" else "Modifier"; render() }
                })
            })
            area = row().apply { gravity = Gravity.FILL }
            addView(area, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
            render()
        }
    }

    fun render() {
        if (!::area.isInitialized) return
        area.removeAllViews()
        val list = ids()
        list.forEachIndexed { index, id ->
            val info = manager.getAppWidgetInfo(id) ?: return@forEachIndexed
            area.addView(card(id, info), LinearLayout.LayoutParams(0, -1, weight(info)).apply {
                if (index > 0) marginStart = home.dp(10)
            })
        }
        if (list.size < MAX) area.addView(addCard(empty = list.isEmpty()), LinearLayout.LayoutParams(0, -1,
            if (list.isEmpty()) 1f else .6f).apply { if (list.isNotEmpty()) marginStart = home.dp(10) })
    }

    // Wider widgets get a wider share of the row.
    private fun weight(info: AppWidgetProviderInfo) = (info.minWidth / home.resources.displayMetrics.density / 110f).coerceIn(1f, 3f)

    private fun card(id: Int, info: AppWidgetProviderInfo): View = with(home) {
        FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(getColor(R.color.home_chip))
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
            }
            val widget = host.createView(home, id, info).apply { setPadding(0, 0, 0, 0) }
            addView(widget, FrameLayout.LayoutParams(-1, -1))
            addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) size(id, v.width, v.height)
            }
            if (editing) addView(label("Retirer", 14, onAccent, 700).apply {
                gravity = Gravity.CENTER
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(accent)
                }, 12)
                setOnClickListener { remove(id) }
            }, FrameLayout.LayoutParams(dp(110), dp(42), Gravity.CENTER))
        }
    }

    /** Tells the widget its real size in dp so it can pick its layout. */
    private fun size(id: Int, widthPx: Int, heightPx: Int) {
        val density = home.resources.displayMetrics.density
        val w = (widthPx / density).toInt()
        val h = (heightPx / density).toInt()
        runCatching {
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(w.toFloat(), h.toFloat())))
                }
            })
        }
    }

    private fun addCard(empty: Boolean): View = with(home) {
        column().apply {
            gravity = Gravity.CENTER
            background = pressable(GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), getColor(R.color.home_card_border), dp(6).toFloat(), dp(4).toFloat())
                setColor(getColor(R.color.home_chip))
            }, 14)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_home_add)
                imageTintList = ColorStateList.valueOf(muted)
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
            if (empty) {
                addView(label("Ajouter un widget", 15, text, 700).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                addView(label("Agenda, musique, horloge… des applis installées", 12, muted).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
            contentDescription = "Ajouter un widget"
            setOnClickListener { showPicker() }
        }
    }

    private fun showPicker() = with(home) {
        val providers = runCatching { manager.installedProviders }.getOrDefault(emptyList())
            .sortedBy { it.loadLabel(packageManager).lowercase() }
        if (ids().size >= MAX) {
            Toast.makeText(this, "Trois widgets au maximum : retire d’abord un widget.", Toast.LENGTH_LONG).show()
            return@with
        }
        openPanel(HomeActivity.Page.WIDGETS, column().apply {
            background = sheet()
            setPadding(dp(16), dp(12), dp(16), dp(10))
            addView(panelHeader("Ajouter un widget", if (providers.isEmpty()) "Aucune appli installée ne propose de widget"
                else "Touche un widget pour le placer sur la deuxième page"))
            addView(GridView(context).apply {
                numColumns = 3
                verticalSpacing = dp(8)
                horizontalSpacing = dp(8)
                selector = GradientDrawable()
                adapter = object : BaseAdapter() {
                    override fun getCount() = providers.size
                    override fun getItem(position: Int) = providers[position]
                    override fun getItemId(position: Int) = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val info = providers[position]
                        val appName = runCatching {
                            packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.provider.packageName, 0))
                        }.getOrNull()?.toString().orEmpty()
                        return row().apply {
                            background = pressable(GradientDrawable().apply {
                                cornerRadius = dp(12).toFloat()
                                setColor(getColor(R.color.home_chip))
                            }, 12)
                            setPadding(dp(10), 0, dp(10), 0)
                            addView(ImageView(context).apply {
                                setImageDrawable(runCatching { info.loadIcon(home, resources.displayMetrics.densityDpi) }.getOrNull())
                            }, LinearLayout.LayoutParams(dp(36), dp(36)))
                            addView(column().apply {
                                addView(label(info.loadLabel(packageManager), 14, text, 700))
                                addView(label(appName, 11, muted), wrap().apply { topMargin = dp(2) })
                            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
                            setOnClickListener { add(info) }
                            layoutParams = ViewGroup.LayoutParams(-1, dp(64))
                        }
                    }
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
        })
    }

    private fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pending = id
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) configure(id)
        else try {
            @Suppress("DEPRECATION")
            home.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile), REQUEST_BIND)
        } catch (_: ActivityNotFoundException) {
            cancel()
            Toast.makeText(home, "Android ne permet pas d’ajouter ce widget ici.", Toast.LENGTH_LONG).show()
        }
    }

    private fun configure(id: Int) {
        val info = manager.getAppWidgetInfo(id) ?: return cancel()
        if (info.configure == null) return finish(id)
        try {
            host.startAppWidgetConfigureActivityForResult(home, id, 0, REQUEST_CONFIGURE, null)
        } catch (_: Exception) {
            // Some widgets cannot be set up by another home screen; they still work with their defaults.
            finish(id)
        }
    }

    private fun finish(id: Int) {
        pending = null
        save(ids() + id)
        home.closePanel()
        render()
    }

    private fun cancel() {
        pending?.let { runCatching { host.deleteAppWidgetId(it) } }
        pending = null
    }

    private fun remove(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
        save(ids() - id)
        render()
    }

    /** Called from the activity's onActivityResult. */
    fun onResult(requestCode: Int, resultCode: Int): Boolean {
        val id = pending ?: return false
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == Activity.RESULT_OK) configure(id) else cancel()
            REQUEST_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finish(id) else cancel()
            else -> return false
        }
        return true
    }

    private companion object {
        const val HOST_ID = 2026
        const val KEY = "widgets"
        const val MAX = 3
        const val REQUEST_BIND = 41
        const val REQUEST_CONFIGURE = 42
    }
}

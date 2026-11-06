@file:android.annotation.SuppressLint("ViewConstructor")

package com.autodrive.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native, recycled cards. Only visible installed icons are read, off the UI thread. */
internal class AppDrawerView(context: Context, private val apps: List<Launchable>,
    private val icon: (Launchable) -> Drawable?, private val open: (Launchable) -> Unit) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(2)
    private val icons = LruCache<String, Drawable>(48)
    private var rows = apps
    private val count = ui.label("${apps.size} applications", 13, Palette.muted).apply { gravity = Gravity.CENTER }
    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            ((convertView as? Card) ?: Card(context)).apply { bind(rows[position]) }
    }
    private val grid = GridView(context).apply {
        adapter = this@AppDrawerView.adapter
        numColumns = ((resources.configuration.screenWidthDp - 24) / 120).coerceIn(2, 6)
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        horizontalSpacing = ui.dp(8); verticalSpacing = ui.dp(8)
        contentDescription = "Grille des applications installées"
        clipToPadding = false; setPadding(0, ui.dp(8), 0, ui.dp(8))
    }

    init {
        orientation = VERTICAL
        addView(ui.line().apply {
            addView(EditText(context).apply {
                hint = "Rechercher une application"; contentDescription = hint
                setSingleLine(true); setTextColor(Palette.text); setHintTextColor(Palette.muted)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setOnEditorActionListener { _, action, event ->
                    if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                        windowInsetsController?.hide(android.view.WindowInsets.Type.ime()); true
                    } else false
                }
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = RadioCatalogue.searchKey(s.toString())
                        rows = apps.filter { query.isEmpty() || RadioCatalogue.searchKey(it.label).contains(query) ||
                            RadioCatalogue.searchKey(it.component.packageName).contains(query) }
                        this@AppDrawerView.count.text = "${rows.size} / ${apps.size}"
                        adapter.notifyDataSetChanged(); grid.setSelection(0)
                    }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                })
            }, LayoutParams(0, ui.dp(48), 1f))
            addView(count, LayoutParams(ui.dp(100), ui.dp(48)))
        })
        addView(FrameLayout(context).apply {
            val empty = ui.label("Aucune application trouvée", 17, Palette.muted).apply { gravity = Gravity.CENTER }
            addView(grid, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -1)); grid.emptyView = empty
        }, LayoutParams(-1, 0, 1f))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val columns = (w / ui.dp(120)).coerceIn(2, 6)
        if (grid.numColumns != columns) {
            grid.numColumns = columns
            grid.invalidateViews() // Re-measure every recycled cell after a column-count change.
        }
    }

    private inner class Card(context: Context) : LinearLayout(context) {
        private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        private val title = ui.label("", 14, bold = true).apply {
            maxLines = 2; gravity = Gravity.CENTER; ellipsize = android.text.TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        private var job: Job? = null
        init {
            orientation = VERTICAL; gravity = Gravity.CENTER
            setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(8))
            layoutParams = android.widget.AbsListView.LayoutParams(-1, ui.dp(112))
            addView(image, LayoutParams(ui.dp(44), ui.dp(44)))
            addView(title, LayoutParams(-1, ui.dp(42)).apply { topMargin = ui.dp(5) })
        }
        fun bind(app: Launchable) {
            job?.cancel()
            title.text = app.label; tooltipText = app.label; contentDescription = "Ouvrir ${app.label}"
            ui.touch(this, Palette.raised, 14, Palette.border) { open(app) }
            val key = app.component.flattenToString()
            image.setImageDrawable(icons.get(key) ?: context.packageManager.defaultActivityIcon)
            if (icons.get(key) == null) job = scope.launch {
                val drawable = withContext(io) { runCatching { icon(app) }.getOrNull() }
                if (drawable != null) { icons.put(key, drawable); image.setImageDrawable(drawable) }
            }
        }
    }
    override fun onDetachedFromWindow() { scope.cancel(); icons.evictAll(); super.onDetachedFromWindow() }
}

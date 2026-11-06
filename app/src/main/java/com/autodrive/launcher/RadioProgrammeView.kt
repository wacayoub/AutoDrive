@file:android.annotation.SuppressLint("ViewConstructor")

package com.autodrive.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal class RadioProgrammeView(context: Context, refresh: () -> Unit, source: (RadioGuideSource) -> Unit) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private var state = RadioGuideState()
    private var selectedDay = Instant.now().atZone(MoroccoRadioTime.zoneAt(Instant.now())).dayOfWeek
    private var followsToday = true
    private val dayTitle = ui.label("", 16, bold = true).apply { gravity = Gravity.CENTER }
    private val note = ui.label("", 11, Palette.muted).apply { maxLines = 2 }
    private val empty = ui.label("Programme indisponible", 16, Palette.muted).apply { maxLines = 5; gravity = Gravity.CENTER }
    private val refreshButton = ui.action("↻", run = refresh).apply { contentDescription = "Actualiser le programme radio" }
    private val sourceButton = ui.action("Source") { state.source?.let(source) }.apply { contentDescription = "Voir la grille officielle" }
    private var rows = emptyList<RadioProgramme>()
    private var plannedNow: RadioProgramme? = null
    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: ui.line().apply {
                minimumHeight = ui.dp(64)
                setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
                addView(ui.label("", 13, Palette.cyan).apply { maxLines = 2; gravity = Gravity.CENTER }, LayoutParams(ui.dp(76), -2))
                addView(ui.line(true).apply {
                    addView(ui.label("", 16, bold = true).apply { maxLines = 2; textDirection = View.TEXT_DIRECTION_FIRST_STRONG; textAlignment = View.TEXT_ALIGNMENT_TEXT_START })
                    addView(ui.label("", 11, Palette.muted))
                }, LayoutParams(0, -2, 1f))
            }
            val programme = rows[position]
            val current = programme == plannedNow
            (row.getChildAt(0) as TextView).text = "${clock(programme.startMinute)}\n${clock(programme.endMinute)}"
            val labels = row.getChildAt(1) as LinearLayout
            (labels.getChildAt(0) as TextView).text = programme.title
            (labels.getChildAt(1) as TextView).text = if (current) "Prévu en ce moment" else "Grille hebdomadaire"
            row.background = ui.shape(if (current) 0xFF244E73.toInt() else Palette.surface, 10,
                if (current) Palette.blue else Palette.border)
            row.contentDescription = "Programme ${programme.title}, ${clock(programme.startMinute)} à ${clock(programme.endMinute)}"
            return row
        }
    }
    private val list = ListView(context).apply { adapter = this@RadioProgrammeView.adapter; dividerHeight = ui.dp(4); contentDescription = "Programme des émissions radio" }

    init {
        orientation = VERTICAL
        ui.touch(dayTitle, Palette.surface, 10) { followsToday = true; render(state, resetScroll = true) }
        dayTitle.contentDescription = "Revenir au programme d’aujourd’hui"
        addView(ui.line().apply {
            addView(ui.action("‹") { changeDay(-1) }.apply { contentDescription = "Programme du jour précédent" }, LayoutParams(ui.dp(48), ui.dp(48)))
            addView(dayTitle, LayoutParams(0, ui.dp(48), 1f))
            addView(ui.action("›") { changeDay(1) }.apply { contentDescription = "Programme du jour suivant" }, LayoutParams(ui.dp(48), ui.dp(48)))
            addView(refreshButton, LayoutParams(ui.dp(48), ui.dp(48)))
            addView(sourceButton, LayoutParams(ui.dp(72), ui.dp(48)))
        })
        addView(note, LayoutParams(-1, ui.dp(34)))
        addView(android.widget.FrameLayout(context).apply {
            addView(list, android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(empty, android.widget.FrameLayout.LayoutParams(-1, -1))
            list.emptyView = empty
        }, LayoutParams(-1, 0, 1f))
    }

    fun render(value: RadioGuideState, now: Instant = Instant.now(), resetScroll: Boolean = false) {
        val sourceChanged = state.source != value.source || (state.guide == null && value.guide != null)
        state = value
        if (followsToday) selectedDay = now.atZone(MoroccoRadioTime.zoneAt(now)).dayOfWeek
        rows = value.guide?.programmes?.filter { it.day == selectedDay }.orEmpty()
        plannedNow = value.guide?.current(now)?.programme?.takeIf { it.day == selectedDay }
        dayTitle.text = selectedDay.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar(Char::titlecase)
        val fetched = value.guide?.fetchedAt?.let { it.atZone(MoroccoRadioTime.zoneAt(it)).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) }
        note.text = if (fetched != null) "Grille publiée · Maroc (GMT) · consultée $fetched${if (value.cached) " · enregistrée" else ""}"
            else if (value.source == RadioGuideSource.ASWAT) "Horaires du Maroc (GMT) · grille publiée par la radio"
            else "Guide horaire non intégré pour cette station"
        if (value.guide != null && !value.guide.freshForPlanning(now)) note.text = "Grille enregistrée ancienne · consultée $fetched · Maroc"
        else if (value.phase == RadioGuidePhase.ERROR) note.text = "$noteTextPrefix${value.message}"
        note.tooltipText = note.text
        refreshButton.isEnabled = value.source == RadioGuideSource.ASWAT && value.phase != RadioGuidePhase.LOADING
        refreshButton.alpha = if (refreshButton.isEnabled) 1f else .4f
        sourceButton.isEnabled = value.source != null; sourceButton.alpha = if (sourceButton.isEnabled) 1f else .4f
        sourceButton.contentDescription = if (value.source == RadioGuideSource.ASWAT) "Voir la grille officielle" else "Ouvrir le site officiel de la station"
        empty.text = when {
            value.phase == RadioGuidePhase.LOADING && value.guide == null -> "Chargement de la grille officielle…"
            value.guide != null && rows.isEmpty() -> "Aucun horaire publié pour ce jour"
            else -> value.message
        }
        adapter.notifyDataSetChanged()
        if (sourceChanged || resetScroll) {
            val position = rows.indexOf(plannedNow).coerceAtLeast(0)
            list.post { list.setSelection(position) }
        }
    }
    private fun changeDay(delta: Long) { followsToday = false; selectedDay = selectedDay.plus(delta); render(state, resetScroll = true) }
    companion object {
        private const val noteTextPrefix = "Grille publiée · "
        fun clock(minute: Int) = if (minute == 1440) "24:00" else "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)
    }
}

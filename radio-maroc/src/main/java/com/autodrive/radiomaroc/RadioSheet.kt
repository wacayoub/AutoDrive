package com.autodrive.radiomaroc

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The radio's own panel for menus, settings and pickers: a rounded dark card with a title,
 * a close button and large rows, instead of the platform's grey list dialogs.
 */
internal class RadioSheet(private val activity: Activity, title: String, subtitle: String? = null) {
    private val compact = activity.resources.configuration.screenHeightDp < 360
    val dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }

    /** Rows and sections go here. */
    val body: LinearLayout = activity.column()

    init {
        val textColor = activity.getColor(R.color.radio_text)
        val root = activity.column().apply {
            setPadding(activity.dp(18), activity.dp(if (compact) 8 else 14), activity.dp(10), activity.dp(if (compact) 10 else 16))
            addView(activity.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(activity.column().apply {
                    addView(activity.label(title, if (compact) 17 else 19, textColor, bold = true).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        isAccessibilityHeading = true
                    })
                    if (subtitle != null) addView(activity.label(subtitle, 12, activity.getColor(R.color.radio_muted)).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ImageButton(activity).apply {
                    setImageResource(R.drawable.ic_radio_close)
                    imageTintList = ColorStateList.valueOf(textColor)
                    background = activity.pressable(R.drawable.radio_round)
                    contentDescription = "Fermer"
                    remoteFocus()
                    setRadioClick { dialog.dismiss() }
                }, LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)))
            }, LinearLayout.LayoutParams(-1, -2).apply { marginEnd = activity.dp(4) })
            // Dialog windows measure their content without a height limit, so cap the list to the screen and let it scroll.
            val maxBody = activity.resources.displayMetrics.heightPixels - activity.dp(if (compact) 120 else 150)
            addView(object : ScrollView(activity) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxBody.coerceAtLeast(activity.dp(120)), MeasureSpec.AT_MOST))
                }
            }.apply {
                isVerticalScrollBarEnabled = true
                isFillViewport = false
                addView(body, ViewGroup.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(if (compact) 6 else 10) })
            body.setPadding(0, 0, activity.dp(8), 0)
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(R.drawable.radio_dialog)
            val metrics = activity.resources.displayMetrics
            val width = minOf(metrics.widthPixels - activity.dp(24), activity.dp(620))
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun show(): RadioSheet = apply {
        dialog.show()
        body.getChildAt(0)?.let { first -> if (activity.isTelevision) first.requestFocus() }
    }

    fun dismiss() = dialog.dismiss()

    /** A section caption above a group of rows. */
    fun caption(text: String) {
        body.addView(activity.label(text, 12, activity.getColor(R.color.radio_accent), bold = true).apply {
            letterSpacing = .06f
            setPadding(activity.dp(4), activity.dp(10), 0, activity.dp(4))
        })
    }

    /** One tappable row; [selected] shows the check mark and highlight. */
    fun option(
        title: String,
        detail: String? = null,
        icon: Int? = null,
        selected: Boolean = false,
        chevron: Boolean = false,
        action: () -> Unit,
    ): View = optionView(title, detail, icon, selected, chevron, action).also {
        body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(6) })
    }

    /** Rows laid out [columns] per line, for short lists such as settings or genres. */
    fun grid(columns: Int, items: List<Pair<String, () -> View>>) {
        items.chunked(columns).forEach { line ->
            body.addView(activity.row().apply {
                isBaselineAligned = false
                line.forEachIndexed { index, (_, make) ->
                    addView(make(), LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index < columns - 1) marginEnd = activity.dp(6)
                    })
                }
                repeat(columns - line.size) { addView(View(activity), LinearLayout.LayoutParams(0, 0, 1f)) }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(6) })
        }
    }

    fun optionView(
        title: String,
        detail: String? = null,
        icon: Int? = null,
        selected: Boolean = false,
        chevron: Boolean = false,
        action: () -> Unit,
    ): View = activity.row().apply {
        val accent = activity.getColor(R.color.radio_accent)
        val textColor = activity.getColor(R.color.radio_text)
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.dp(if (compact) 48 else 54)
        setPadding(activity.dp(12), activity.dp(6), activity.dp(10), activity.dp(6))
        background = activity.pressable(R.drawable.radio_option)
        isFocusable = true
        remoteFocus()
        contentDescription = listOfNotNull(title, detail).joinToString(" · ")
        if (icon != null) addView(ImageView(activity).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(accent)
            scaleType = ImageView.ScaleType.CENTER
            background = activity.getDrawable(R.drawable.radio_icon_badge)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(activity.dp(38), activity.dp(38)).apply { marginEnd = activity.dp(12) })
        val titleView = activity.label(title, 15, if (selected) accent else textColor, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        addView(activity.column().apply {
            addView(titleView)
            if (!detail.isNullOrEmpty()) addView(activity.label(detail, 12, activity.getColor(R.color.radio_muted)).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val trailing = ImageView(activity).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        addView(trailing, LinearLayout.LayoutParams(activity.dp(22), activity.dp(22)).apply { marginStart = activity.dp(6) })
        tag = OptionParts(titleView, trailing, chevron)
        markSelected(this, selected)
        setRadioClick(action)
    }

    private class OptionParts(val title: TextView, val trailing: ImageView, val chevron: Boolean)

    /** Moves the highlight and check mark without rebuilding the panel. */
    fun markSelected(row: View, selected: Boolean) {
        val parts = row.tag as? OptionParts ?: return
        val accent = activity.getColor(R.color.radio_accent)
        row.isSelected = selected
        row.stateDescription = if (selected) "Sélectionné" else null
        parts.title.setTextColor(if (selected) accent else activity.getColor(R.color.radio_text))
        val icon = if (selected) R.drawable.ic_radio_check else if (parts.chevron) R.drawable.ic_radio_chevron else null
        parts.trailing.visibility = if (icon == null) View.GONE else View.VISIBLE
        icon?.let(parts.trailing::setImageResource)
        parts.trailing.imageTintList = ColorStateList.valueOf(if (selected) accent else activity.getColor(R.color.radio_muted))
    }
}

/** A single-choice picker: tapping an option applies it and closes the panel. */
internal fun Activity.showChoiceSheet(
    title: String,
    options: List<String>,
    selected: Int,
    subtitle: String? = null,
    details: List<String?> = emptyList(),
    columns: Int = 1,
    pick: (Int) -> Unit,
): RadioSheet {
    val sheet = RadioSheet(this, title, subtitle)
    fun make(index: Int) = sheet.optionView(options[index], details.getOrNull(index), selected = index == selected) {
        sheet.dismiss()
        if (index != selected) pick(index)
    }
    if (columns <= 1) {
        options.indices.forEach { index ->
            sheet.body.addView(make(index), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
    } else {
        sheet.grid(columns, options.indices.map { index -> options[index] to { make(index) } })
    }
    return sheet.show().also { shown ->
        if (selected >= 0) shown.body.post {
            (shown.body.parent as? ScrollView)?.let { scroll ->
                val target = (0 until shown.body.childCount).map(shown.body::getChildAt)
                    .firstOrNull { it.isSelected || (it as? ViewGroup)?.let { line -> (0 until line.childCount).any { i -> line.getChildAt(i).isSelected } } == true }
                target?.let { scroll.smoothScrollTo(0, (it.top - dp(8)).coerceAtLeast(0)) }
            }
        }
    }
}

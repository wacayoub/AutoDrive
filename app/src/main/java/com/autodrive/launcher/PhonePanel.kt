package com.autodrive.launcher

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout

internal class PhonePanel(context: Context, initialNumber: String, favorites: List<PhoneFavorite>, appLabel: String,
    changed: (String) -> Unit, dial: (String) -> Unit, chooseApp: () -> Unit, openApp: () -> Unit,
    saveFavorite: (String) -> Unit, removeFavorite: (PhoneFavorite) -> Unit) : LinearLayout(context) {
    private val ui = DriveViews(context)
    val number = EditText(context).apply {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_PHONE
        setTextColor(Palette.text)
        setTextSize(24f)
        hint = "Numéro de téléphone"
        setHintTextColor(Palette.muted)
        contentDescription = "Numéro de téléphone"
        showSoftInputOnFocus = false
        setText(initialNumber)
        setSelection(text.length)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }
    init {
        val portrait = resources.configuration.screenHeightDp > resources.configuration.screenWidthDp
        orientation = if (portrait) VERTICAL else HORIZONTAL
        fun column() = ui.line(true).apply {
            background = ui.shape(Palette.surface, 16, Palette.border)
            setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(8))
        }
        addView(column().apply {
            addView(ui.line().apply {
                addView(number, LayoutParams(0, ui.dp(48), 1f))
                addView(ui.action("⌫") {
                    val start = number.selectionStart.coerceAtLeast(0)
                    val end = number.selectionEnd.coerceAtLeast(start)
                    if (start != end) number.text.delete(start, end) else if (start > 0) number.text.delete(start - 1, start)
                }.apply { contentDescription = "Effacer un chiffre"; setOnLongClickListener { number.setText(""); true } }, LayoutParams(ui.dp(56), ui.dp(48)))
            })
            for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))) {
                addView(ui.line().apply {
                    row.forEach { key -> addView(ui.action(key) { insert(key) }.apply {
                        if (key == "0") setOnLongClickListener { insert("+"); true }
                    }, LayoutParams(0, ui.dp(48), 1f).apply { marginEnd = ui.dp(3); topMargin = ui.dp(3) }) }
                })
            }
            addView(ui.action("Préparer l’appel", "phone", iconColor = Palette.green) { dial(number.text.toString()) },
                LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(6) })
        }, if (portrait) LayoutParams(-1, -2) else LayoutParams(0, -2, 1.1f).apply { marginEnd = ui.dp(10) })
        addView(column().apply {
            addView(ui.label("Favoris", 20, bold = true), LayoutParams(-1, ui.dp(30)))
            if (favorites.isEmpty()) addView(ui.label("Ajoute tes numéros habituels", 14, Palette.muted), LayoutParams(-1, ui.dp(30)))
            favorites.forEach { favorite ->
                addView(ui.action(favorite.name, "phone", iconColor = Palette.green) {
                    number.setText(favorite.number); number.setSelection(number.text.length)
                }.apply { contentDescription = "Favori ${favorite.name}"; setOnLongClickListener { removeFavorite(favorite); true } },
                    LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(4) })
            }
            addView(ui.action("Ajouter aux favoris") { saveFavorite(number.text.toString()) }, LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(6) })
            addView(ui.action("Application · $appLabel", run = chooseApp), LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(6) })
            addView(ui.action("Ouvrir le téléphone", "phone", iconColor = Palette.green, run = openApp), LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(6) })
            addView(ui.label("0 prolongé : + · L’application téléphone confirme l’appel. Bluetooth selon le poste.", 12, Palette.muted).apply { maxLines = 3 }, LayoutParams(-1, ui.dp(48)))
        }, if (portrait) LayoutParams(-1, -2).apply { topMargin = ui.dp(10) } else LayoutParams(0, -2, 1f))
    }
    private fun insert(value: String) {
        if (number.text.length >= 40) return
        val start = number.selectionStart.coerceAtLeast(0)
        val end = number.selectionEnd.coerceAtLeast(start)
        number.text.replace(start, end, value)
    }
}

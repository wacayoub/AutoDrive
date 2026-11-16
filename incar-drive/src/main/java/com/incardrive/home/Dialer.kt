package com.incardrive.home

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** A number saved in Incar Drive for one-tap calls. */
internal data class SavedNumber(val name: String, val number: String) {
    companion object {
        fun decode(value: String?): List<SavedNumber> = value.orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts[1].isNotBlank()) SavedNumber(parts[0], parts[1]) else null
        }

        fun encode(list: List<SavedNumber>) = list.joinToString("\n") {
            "${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.number}"
        }

        /** Keeps digits, a leading +, and the * and # of service codes. */
        fun clean(number: String) = number.trim().let { raw ->
            (if (raw.startsWith("+")) "+" else "") + raw.filter { it.isDigit() || it == '*' || it == '#' }
        }
    }
}

/**
 * The phone page: a keypad and the numbers saved in Incar Drive. Incar Drive cannot place a Bluetooth call itself,
 * so « Appeler » hands the number to the phone app with Android's DIAL request. If the head unit's phone app does not
 * take numbers from other apps, it is opened instead, with the number copied and shown so it can be dialled there.
 */
internal class Dialer(private val home: HomeActivity) {
    private var typed = ""
    private lateinit var numberView: TextView

    fun saved(): List<SavedNumber> = SavedNumber.decode(home.prefs.getString(KEY, null))

    private fun save(list: List<SavedNumber>) = home.prefs.edit().putString(KEY, SavedNumber.encode(list)).apply()

    fun show(number: String = "") = with(home) {
        typed = number
        openPanel(HomeActivity.Page.DIALER, row().apply {
            background = sheet()
            gravity = Gravity.FILL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(keypad(), LinearLayout.LayoutParams(0, -1, 1.15f))
            addView(favourites(), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(16) })
        })
    }

    private fun keypad(): View = with(home) {
        column().apply {
            numberView = label("", 30, text, 500, condensed = true).apply {
                gravity = Gravity.CENTER
                letterSpacing = .04f
            }
            addView(row().apply {
                addView(numberView, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ImageButton(context).apply {
                    setImageResource(R.drawable.ic_home_backspace)
                    imageTintList = ColorStateList.valueOf(muted)
                    background = pressable(null, 12)
                    contentDescription = "Effacer"
                    setOnClickListener { typed = typed.dropLast(1); refresh() }
                    setOnLongClickListener { typed = ""; refresh(); true }
                }, LinearLayout.LayoutParams(dp(48), dp(44)))
            }, LinearLayout.LayoutParams(-1, dp(44)))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#")).forEach { keys ->
                addView(row().apply {
                    gravity = Gravity.FILL
                    keys.forEachIndexed { index, key ->
                        addView(label(key, 24, text, 600, condensed = true).apply {
                            gravity = Gravity.CENTER
                            background = pressable(GradientDrawable().apply {
                                cornerRadius = dp(12).toFloat()
                                setColor(getColor(R.color.home_chip))
                                setStroke(dp(1), getColor(R.color.home_card_border))
                            }, 12)
                            setOnClickListener { if (typed.length < 20) typed += key; refresh() }
                            // A long press on 0 types the + of international numbers.
                            if (key == "0") setOnLongClickListener { if (typed.isEmpty()) typed = "+"; refresh(); true }
                        }, LinearLayout.LayoutParams(0, -1, 1f).apply { if (index > 0) marginStart = dp(8) })
                    }
                }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
            }
            addView(row().apply {
                gravity = Gravity.FILL
                addView(label("Enregistrer", 14, text, 700).apply {
                    gravity = Gravity.CENTER
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(getColor(R.color.home_chip))
                    }, 12)
                    setOnClickListener { edit(null, typed) }
                }, LinearLayout.LayoutParams(0, -1, 1f))
                addView(callButton("Appeler") { call(typed) }, LinearLayout.LayoutParams(0, -1, 2f).apply { marginStart = dp(8) })
            }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
            refresh()
        }
    }

    private fun callButton(title: String, action: () -> Unit) = with(home) {
        row().apply {
            gravity = Gravity.CENTER
            // See-through green with a green phone and text, like the phone button of the dock.
            background = pressable(GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(tint(phoneColor, if (night) 0x38 else 0x26))
                setStroke(dp(1), tint(phoneColor, 0x80))
            }, 12)
            addView(android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_home_phone)
                imageTintList = ColorStateList.valueOf(phoneColor)
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(label(title, 16, phoneColor, 700), wrap().apply { marginStart = dp(10) })
            contentDescription = title
            setOnClickListener { action() }
        }
    }

    private fun refresh() {
        if (!::numberView.isInitialized) return
        numberView.text = typed.ifEmpty { "" }
        numberView.hint = "Numéro"
        numberView.setHintTextColor(home.muted)
    }

    private fun favourites(): View = with(home) {
        column().apply {
            addView(row().apply {
                addView(caps("Numéros enregistrés", phoneColor), LinearLayout.LayoutParams(0, -2, 1f))
                addView(round(R.drawable.ic_home_close, "Fermer") { closePanel() }, LinearLayout.LayoutParams(dp(40), dp(40)))
            })
            val list = column()
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(list)
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
            val numbers = saved()
            if (numbers.isEmpty()) list.addView(label("Tape un numéro puis « Enregistrer » pour l’appeler ensuite en un geste.", 13, muted).apply {
                maxLines = 3
                setPadding(0, dp(8), 0, 0)
            })
            numbers.forEach { entry ->
                list.addView(row().apply {
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(getColor(R.color.home_chip))
                    }, 12)
                    setPadding(dp(12), 0, dp(6), 0)
                    addView(column().apply {
                        addView(label(entry.name, 15, text, 700))
                        addView(label(entry.number, 12, muted), wrap().apply { topMargin = dp(2) })
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(FrameLayout(context).apply {
                        background = pressable(GradientDrawable().apply {
                            cornerRadius = dp(10).toFloat()
                            setColor(tint(phoneColor, if (night) 0x38 else 0x26))
                        }, 10)
                        addView(android.widget.ImageView(context).apply {
                            setImageResource(R.drawable.ic_home_phone)
                            imageTintList = ColorStateList.valueOf(phoneColor)
                        }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                        contentDescription = "Appeler ${entry.name}"
                        setOnClickListener { call(entry.number) }
                    }, LinearLayout.LayoutParams(dp(44), dp(40)))
                    contentDescription = entry.name
                    setOnClickListener { call(entry.number) }
                    setOnLongClickListener { edit(entry, entry.number); true }
                }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(6) })
            }
            addView(label("Ouvrir l’appli Téléphone  ›", 14, text, 700).apply {
                gravity = Gravity.CENTER
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(getColor(R.color.home_chip))
                    setStroke(dp(1), getColor(R.color.home_card_border))
                }, 12)
                setOnClickListener {
                    if (!links.openPhone()) Toast.makeText(context, "Aucune application de téléphone trouvée.", Toast.LENGTH_LONG).show()
                }
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        }
    }

    /** Adds a saved number, or renames, changes or removes one (empty number removes it). */
    private fun edit(existing: SavedNumber?, number: String) = with(home) {
        val name = EditText(this).apply {
            hint = "Nom"
            setText(existing?.name.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        val digits = EditText(this).apply {
            hint = "Numéro"
            setText(number)
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Enregistrer un numéro" else existing.name)
            .setView(column().apply {
                setPadding(dp(20), dp(4), dp(20), 0)
                addView(name)
                addView(digits)
            })
            .setPositiveButton("Enregistrer") { _, _ ->
                val cleaned = SavedNumber.clean(digits.text.toString())
                val list = saved().filter { it != existing }.toMutableList()
                if (cleaned.isNotEmpty()) list += SavedNumber(name.text.toString().trim().ifEmpty { cleaned }, cleaned)
                save(list.sortedBy { it.name.lowercase() })
                show(typed)
            }
            .setNegativeButton("Annuler", null)
        if (existing != null) dialog.setNeutralButton("Supprimer") { _, _ ->
            save(saved().filter { it != existing })
            show(typed)
        }
        dialog.show()
    }

    /** Hands the number to the phone app, or opens the phone app with the number copied when it cannot take it. */
    fun call(raw: String) = with(home) {
        val number = SavedNumber.clean(raw)
        if (number.isEmpty()) {
            Toast.makeText(this, "Tape d’abord un numéro.", Toast.LENGTH_SHORT).show()
            return@with
        }
        if (links.dial(number) != null) return@with
        runCatching {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Numéro", number))
        }
        if (links.openPhone()) Toast.makeText(this, "Compose $number dans l’appli Téléphone (numéro copié).", Toast.LENGTH_LONG).show()
        else Toast.makeText(this, "Aucune application de téléphone trouvée.", Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val KEY = "saved_numbers"
    }
}

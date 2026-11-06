@file:android.annotation.SuppressLint("ViewConstructor") // Programmatic views only.

package com.autodrive.launcher

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView

internal class PhonePanel(context: Context, initialNumber: String, private val favorites: List<PhoneFavorite>, appLabel: String,
    changed: (String) -> Unit, dial: (String) -> Unit, chooseApp: () -> Unit, openApp: () -> Unit,
    private val saveFavorite: (String) -> Unit, private val removeFavorite: (PhoneFavorite) -> Unit,
    dialLabel: String = "Préparer l’appel", bluetoothStatus: String? = null, configureBluetooth: () -> Unit = {}, private val requestContacts: () -> Unit = {}, private val refreshContacts: () -> Unit = {}) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private var contactsState = PhoneContactsState()
    private var showingFavorites = favorites.isNotEmpty()
    private val contactBody = FrameLayout(context)
    private val contactTabs = ui.line()
    private val search = EditText(context).apply {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        setTextColor(Palette.text); setHintTextColor(Palette.muted)
        textSize = 16f; hint = "Nom ou numéro"; contentDescription = "Rechercher un contact"
    }
    val number = EditText(context).apply {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_PHONE
        setTextColor(Palette.text)
        setTextSize(24f)
        hint = "Numéro de téléphone"
        setHintTextColor(Palette.muted)
        contentDescription = "Numéro de téléphone"
        showSoftInputOnFocus = true
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
        val shortLandscape = !portrait && resources.configuration.screenHeightDp < 430
        orientation = if (portrait) VERTICAL else HORIZONTAL
        isFocusableInTouchMode = true
        fun column() = ui.line(true).apply {
            background = ui.shape(Palette.surface, 16, Palette.border)
            if (!shortLandscape) setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(8))
        }
        addView(column().apply {
            addView(ui.line().apply {
                addView(number, LayoutParams(0, ui.dp(48), 1f))
                addView(ui.action("⌫") { erase() }.apply {
                    contentDescription = "Effacer un chiffre"; isFocusable = false
                    setOnLongClickListener { fill(""); true }
                }, LayoutParams(ui.dp(56), ui.dp(48)))
            })
            for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))) {
                addView(ui.line().apply {
                    row.forEach { key -> addView(ui.action(key) { insert(key) }.apply {
                        isFocusable = false
                        if (key == "0") setOnLongClickListener { insert("+"); true }
                    }, LayoutParams(0, ui.dp(48), 1f).apply { if (!shortLandscape) { marginEnd = ui.dp(3); topMargin = ui.dp(3) } }) }
                })
            }
            addView(ui.action(dialLabel, "phone", iconColor = Palette.green) { dial(number.text.toString()) },
                LayoutParams(-1, ui.dp(48)).apply { if (!shortLandscape) topMargin = ui.dp(6) })
            if (!shortLandscape) addView(ui.label("0 prolongé : + · Appui sur le numéro : clavier", 12, Palette.muted), LayoutParams(-1, ui.dp(24)))
        }, if (portrait) LayoutParams(-1, -2) else LayoutParams(0, -2, 1.1f).apply { marginEnd = ui.dp(10) })
        addView(column().apply {
            addView(ui.line().apply {
                addView(ui.action(appLabel, "phone", iconColor = Palette.green, run = openApp).apply {
                    contentDescription = "Ouvrir le téléphone"
                    tooltipText = appLabel
                    (getChildAt(1) as? android.widget.TextView)?.apply {
                        maxLines = 2; textSize = 15f
                        layoutParams = LayoutParams(0, -2, 1f)
                    }
                }, LayoutParams(0, ui.dp(48), 1f))
                addView(ui.action("", "settings", run = chooseApp).apply { contentDescription = "Choisir l’application téléphone" }, LayoutParams(ui.dp(48), ui.dp(48)))
            })
            if (bluetoothStatus != null) addView(ui.action(bluetoothStatus, run = configureBluetooth).apply {
                contentDescription = "Appels Bluetooth"
                (getChildAt(0) as android.widget.TextView).apply { textSize = 13f; maxLines = 2 }
            }, LayoutParams(-1, ui.dp(48)))
            addView(contactTabs, LayoutParams(-1, ui.dp(48)).apply { if (!shortLandscape) topMargin = ui.dp(4) })
            addView(search, LayoutParams(-1, ui.dp(48)))
            addView(contactBody, LayoutParams(-1, ui.dp(if (shortLandscape) 144 else 188)))
        }, if (portrait) LayoutParams(-1, -2).apply { topMargin = ui.dp(10) } else LayoutParams(0, -2, 1f))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderContacts() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderTabs()
        renderContacts()
    }

    fun render(state: PhoneContactsState) { contactsState = state; renderContacts() }

    private fun renderTabs() {
        contactTabs.removeAllViews()
        listOf(false to "Contacts", true to "Favoris").forEach { (favorite, label) ->
            contactTabs.addView(ui.action(label, selected = favorite == showingFavorites) {
                showingFavorites = favorite; renderTabs(); renderContacts()
            }, LayoutParams(0, -1, 1f))
        }
        search.visibility = if (showingFavorites) View.GONE else View.VISIBLE
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility") // Listener returns false; ListView dispatches accessible item clicks.
    private fun renderContacts() {
        contactBody.removeAllViews()
        if (showingFavorites) {
            contactBody.addView(ui.line(true).apply {
                val list = android.widget.ScrollView(context).apply {
                    addView(ui.line(true).apply {
                        if (favorites.isEmpty()) addView(ui.label("Aucun favori enregistré", 14, Palette.muted))
                        favorites.forEach { favorite -> addView(ui.action(favorite.name, "phone", iconColor = Palette.green) {
                            fill(favorite.number)
                        }.apply {
                            contentDescription = "Favori ${favorite.name}"
                            setOnLongClickListener { removeFavorite(favorite); true }
                        }, LayoutParams(-1, ui.dp(48)).apply { bottomMargin = ui.dp(3) }) }
                    })
                }
                addView(list, LayoutParams(-1, 0, 1f))
                addView(ui.action("Ajouter aux favoris") { saveFavorite(number.text.toString()) }, LayoutParams(-1, ui.dp(48)))
            })
            return
        }
        val state = contactsState
        when (state.phase) {
            ContactsPhase.LOCKED -> notice("Contacts du poste\nAutorise leur lecture pour les afficher ici.", "Autoriser les contacts", requestContacts)
            ContactsPhase.LOADING -> notice("Lecture des contacts du poste…")
            ContactsPhase.UNAVAILABLE -> notice("Carnet Android inaccessible. Utilise le carnet de l’application Bluetooth.", "Actualiser", refreshContacts)
            ContactsPhase.READY -> {
                if (state.contacts.isEmpty()) {
                    notice("Aucun contact partagé avec Android. Le carnet Bluetooth peut rester dans BT Phone.", "Actualiser", refreshContacts)
                } else {
                    val query = search.text.toString().trim()
                    val digits = query.filter(Char::isDigit)
                    val rows = state.contacts.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) ||
                        (digits.isNotEmpty() && query.none(Char::isLetter) && it.number.contains(digits)) }
                    contactBody.addView(ui.line(true).apply {
                        addView(ui.line().apply {
                            addView(ui.label("${rows.size} numéros du poste${if (state.limited) " · limite 5 000" else ""}", 12, Palette.muted), LayoutParams(0, -1, 1f))
                            addView(ui.action("↻", run = refreshContacts).apply { contentDescription = "Actualiser les contacts" }, LayoutParams(ui.dp(48), -1))
                        }, LayoutParams(-1, ui.dp(48)))
                        if (rows.isEmpty()) addView(ui.label("Aucun résultat", 15, Palette.muted))
                        else addView(ListView(context).apply {
                            contentDescription = "Contacts du poste"
                            dividerHeight = ui.dp(1)
                            adapter = object : BaseAdapter() {
                                override fun getCount() = rows.size
                                override fun getItem(position: Int) = rows[position]
                                override fun getItemId(position: Int) = position.toLong()
                                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                    val row = (convertView as? LinearLayout) ?: ui.line(true).apply {
                                        setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
                                        addView(ui.label("", 16, bold = true)); addView(ui.label("", 13, Palette.muted))
                                        layoutParams = android.widget.AbsListView.LayoutParams(-1, ui.dp(52))
                                        gravity = Gravity.CENTER_VERTICAL
                                    }
                                    val contact = rows[position]
                                    (row.getChildAt(0) as android.widget.TextView).text = contact.name
                                    (row.getChildAt(1) as android.widget.TextView).text = contact.number
                                    row.contentDescription = "Contact ${contact.name}, ${contact.number}"
                                    return row
                                }
                            }
                            setOnItemClickListener { _, _, position, _ -> fill(rows[position].number) }
                            setOnTouchListener { view, event ->
                                view.parent.requestDisallowInterceptTouchEvent(event.action != android.view.MotionEvent.ACTION_UP && event.action != android.view.MotionEvent.ACTION_CANCEL)
                                false
                            }
                        }, LayoutParams(-1, 0, 1f))
                    })
                }
            }
        }
    }

    private fun notice(text: String, action: String? = null, run: () -> Unit = {}) {
        contactBody.addView(ui.line(true).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.label(text, 14, Palette.muted).apply { maxLines = 4 }, LayoutParams(-1, 0, 1f))
            if (action != null) addView(ui.action(action, run = run), LayoutParams(-1, ui.dp(48)))
        })
    }

    private fun fill(value: String) { number.setText(value); number.setSelection(number.text.length) }
    private fun selection(): Pair<Int, Int> {
        val length = number.text.length
        val a = number.selectionStart.takeIf { it >= 0 }?.coerceAtMost(length) ?: length
        val b = number.selectionEnd.takeIf { it >= 0 }?.coerceAtMost(length) ?: a
        return minOf(a, b) to maxOf(a, b)
    }
    private fun insert(value: String) {
        val (start, end) = selection()
        if (number.text.length - (end - start) + value.length > 40) return
        number.text.replace(start, end, value)
        number.setSelection(start + value.length)
    }
    private fun erase() {
        val (start, end) = selection()
        val from = if (start == end && start > 0) start - 1 else start
        number.text.delete(from, end)
        number.setSelection(from)
    }
}

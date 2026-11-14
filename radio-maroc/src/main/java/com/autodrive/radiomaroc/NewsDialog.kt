package com.autodrive.radiomaroc

import android.app.AlertDialog
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.autodrive.launcher.RadioArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Headlines from the playing station's [publisher] (when it has one) and the RFI topics.
 * [stationHeadlines] is the feed already polled for the ticker, so opening the dialog costs no extra request.
 */
internal fun ComponentActivity.showNewsDialog(
    publisher: NewsPublisher?,
    stationHeadlines: StateFlow<List<NewsHeadline>>,
    artwork: RadioArtwork,
    compact: Boolean,
) {
    val accent = getColor(R.color.radio_accent)
    val textColor = getColor(R.color.radio_text)
    val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(Locale.FRENCH).withZone(RadioLocalInfo.moroccoTime)
    val cache = mutableMapOf<NewsTopic, List<NewsHeadline>>()
    var request: Job? = null
    val status = label("", 14, getColor(R.color.radio_muted)).apply { gravity = Gravity.CENTER }
    val headlines = object : ArrayAdapter<NewsHeadline>(this, android.R.layout.simple_list_item_1) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val article = requireNotNull(getItem(position))
            return ((convertView as? NewsStoryRow) ?: NewsStoryRow(context, artwork, compact)).apply { bind(article, dateFormat) }
        }
    }
    val list = ListView(this).apply {
        id = View.generateViewId()
        adapter = headlines
        setSelector(R.drawable.radio_focus)
        setOnItemClickListener { _, _, position, _ ->
            val article = requireNotNull(headlines.getItem(position))
            val source = listOfNotNull(article.publisher.label, article.publishedAt?.let(dateFormat::format)).joinToString(" · ")
            AlertDialog.Builder(this@showNewsDialog).setTitle(article.title)
                .setMessage(listOfNotNull(source, article.summary).joinToString("\n\n"))
                .setPositiveButton("Retour", null)
                .setNeutralButton("Lire l’article") { _, _ ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, article.url.toUri())) }
                        .onFailure { Toast.makeText(this@showNewsDialog, "Navigateur indisponible", Toast.LENGTH_SHORT).show() }
                }.show().window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
        }
    }
    val buttons = mutableMapOf<NewsTopic?, TextView>()
    fun load(topic: NewsTopic?) {
        request?.cancel()
        buttons.forEach { (choice, control) ->
            control.isSelected = choice == topic
            control.setTextColor(if (choice == topic) accent else textColor)
        }
        list.nextFocusUpId = buttons.getValue(topic).id
        headlines.clear()
        list.setSelection(0)
        if (topic == null) {
            status.text = "Chargement des titres…"
            request = lifecycleScope.launch {
                stationHeadlines.collect { current ->
                    val articles = current.filter { it.publisher == publisher }
                    headlines.clear()
                    if (articles.isEmpty()) {
                        status.text = "Titres de la station indisponibles. Choisis une rubrique RFI."
                    } else headlines.addAll(articles)
                }
            }
            return
        }
        cache[topic]?.let {
            headlines.addAll(it)
            return
        }
        status.text = "Chargement des titres…"
        request = lifecycleScope.launch {
            val articles = withContext(Dispatchers.IO) { runCatching { RadioNews.fetch(topic) }.getOrDefault(emptyList()) }
            if (articles.isEmpty()) {
                status.text = "Titres indisponibles. Sélectionne la rubrique pour réessayer."
            } else {
                cache[topic] = articles
                headlines.addAll(articles)
            }
        }
    }
    val content = column().apply {
        setPadding(dp(16), dp(4), dp(16), 0)
        addView(row().apply {
            val topics = if (publisher == null) NewsTopic.entries else listOf(null) + NewsTopic.entries
            topics.forEach { topic ->
                val control = button(topic?.label ?: "Station") { load(topic) }.apply {
                    id = View.generateViewId()
                    textSize = if (compact && publisher != null) 11f else 12f
                    maxLines = 1
                    nextFocusDownId = list.id
                }
                buttons[topic] = control
                addView(control, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(4) })
            }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        addView(FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-1, -1))
            list.emptyView = status
        }, LinearLayout.LayoutParams(-1, dp(if (compact) 150 else 240)).apply { topMargin = dp(6) })
    }
    val dialog = AlertDialog.Builder(this).setTitle("Actualités · ${publisher?.label ?: "RFI"}").setView(content)
        .setPositiveButton("Fermer", null).show()
    dialog.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
    dialog.setOnDismissListener { request?.cancel() }
    val initial = if (publisher == null) NewsTopic.WORLD else null
    load(initial)
    buttons.getValue(initial).requestFocus()
}

@SuppressLint("ViewConstructor") // Built in code only, never inflated.
private class NewsStoryRow(context: Context, private val artwork: RadioArtwork, compact: Boolean) : LinearLayout(context) {
    private val title = context.label("", if (compact) 14 else 16, context.getColor(R.color.radio_text), bold = true).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val metadata = context.label("", 12, context.getColor(R.color.radio_muted)).apply { maxLines = 1 }
    private val photo = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply {
            cornerRadius = context.dp(6).toFloat()
            setColor(context.getColor(R.color.radio_control))
        }
        clipToOutline = true
    }
    private var photoTask: Job? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        minimumHeight = context.dp(58)
        addView(photo, LayoutParams(context.dp(72), context.dp(46)).apply { marginEnd = context.dp(10) })
        addView(context.column().apply {
            addView(title, LayoutParams(-1, -2))
            addView(metadata, LayoutParams(-1, -2))
        }, LayoutParams(0, -2, 1f))
    }

    fun bind(article: NewsHeadline, dateFormat: DateTimeFormatter) {
        title.text = article.title
        metadata.text = listOfNotNull(article.publisher.label, article.publishedAt?.let(dateFormat::format)).joinToString(" · ")
        photoTask?.cancel()
        photo.setImageDrawable(null)
        photo.visibility = if (article.imageUrl == null) View.GONE else View.VISIBLE
        photoTask = article.imageUrl?.let { url ->
            artwork.load(url, context.dp(92)) { bitmap ->
                photo.setImageBitmap(bitmap)
                photo.visibility = if (bitmap == null) View.GONE else View.VISIBLE
            }
        }
    }
}

package com.incardrive.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.session.PlaybackState
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.autodrive.radiomaroc.RadioMotion

/**
 * The media card while Radio Maroc & Coran plays, drawn like the radio's own player: the station logo large and
 * unframed, its name, genre and the red LIVE badge, the radio's coloured level meter under the logo, the « RADIO » strip, and the round
 * buttons: previous, play in a large filled circle, next.
 * The card glows in the logo's colour (set by the activity).
 */
internal class RadioCard(private val home: HomeActivity, private val command: (Long) -> Unit) {
    private lateinit var logo: ImageView
    private lateinit var name: TextView
    private lateinit var genre: TextView
    private lateinit var live: View
    private lateinit var strip: TextView
    private lateinit var bars: LevelMeter
    private lateinit var play: ImageButton

    // The radio's own colours: white controls and a black play button by day, dark controls and mint by night.
    private val control get() = if (home.night) 0xFF26282B.toInt() else 0xC7FFFFFF.toInt()
    private val controlBorder get() = if (home.night) 0xFF3C3F44.toInt() else 0xB8D6CFC5.toInt()
    private val primary get() = if (home.night) 0xFF5FD8B6.toInt() else 0xFF16171A.toInt()
    private val onPrimary get() = if (home.night) 0xFF0E2A22.toInt() else Color.WHITE
    private val green get() = if (home.night) 0xFF5FD8B6.toInt() else 0xFF0E8A63.toInt()
    private val stripColor get() = if (home.night) 0xFF26282B.toInt() else 0xFFEEE9E2.toInt()

    fun view(): View = with(home) {
        column().apply {
            addView(row().apply {
                gravity = Gravity.TOP
                addView(column().apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    addView(logo, LinearLayout.LayoutParams(dp(118), dp(96)))
                    bars = LevelMeter(context, night)
                    addView(bars, LinearLayout.LayoutParams(dp(80), dp(26)).apply { topMargin = dp(6) })
                }, LinearLayout.LayoutParams(dp(124), -2))
                addView(column().apply {
                    addView(caps("Radio Maroc", green))
                    name = label("", 26, text, 700, condensed = true).apply { maxLines = 2 }
                    genre = label("", 14, muted, 500)
                    addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    addView(genre, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
                    live = row().apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(0xFFC51D32.toInt()) }
                        setPadding(dp(7), 0, dp(8), 0)
                        addView(View(context).apply {
                            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                        }, LinearLayout.LayoutParams(dp(5), dp(5)).apply { marginEnd = dp(5) })
                        addView(label("LIVE", 12, Color.WHITE, 700).apply { letterSpacing = .06f })
                        contentDescription = "Radio en direct"
                    }
                    addView(live, LinearLayout.LayoutParams(-2, dp(22)).apply { topMargin = dp(8) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10); topMargin = dp(6) })
            })
            addView(View(context), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(row().apply {
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(stripColor) }
                setPadding(dp(6), 0, dp(10), 0)
                addView(label("RADIO", 11, muted, 700).apply {
                    gravity = Gravity.CENTER
                    letterSpacing = .06f
                    setPadding(dp(8), 0, dp(8), 0)
                    background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(home.tint(home.text, 0x14)) }
                }, LinearLayout.LayoutParams(-2, dp(24)))
                strip = label("", 14, text, 500)
                addView(strip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            }, LinearLayout.LayoutParams(-1, dp(38)).apply { bottomMargin = dp(10) })
            addView(row().apply {
                gravity = Gravity.CENTER
                fun button(icon: Int, description: String, main: Boolean = false, action: () -> Unit) = ImageButton(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(if (main) onPrimary else text)
                    background = pressable(GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (main) primary else control)
                        setStroke(dp(1), if (main) primary else controlBorder)
                    }, 40)
                    contentDescription = description
                    setOnClickListener { action() }
                    RadioMotion.pressScale(this, .9f)
                }
                addView(button(R.drawable.ic_home_previous, "Station précédente") { command(PlaybackState.ACTION_SKIP_TO_PREVIOUS) },
                    LinearLayout.LayoutParams(dp(52), dp(52)))
                play = button(R.drawable.ic_home_play, "Écouter", main = true) { command(PlaybackState.ACTION_PLAY_PAUSE) }
                addView(play, LinearLayout.LayoutParams(dp(66), dp(66)).apply { marginStart = dp(14); marginEnd = dp(14) })
                addView(button(R.drawable.ic_home_next, "Station suivante") { command(PlaybackState.ACTION_SKIP_TO_NEXT) },
                    LinearLayout.LayoutParams(dp(52), dp(52)))
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    fun bind(state: MediaState) {
        if (!::name.isInitialized) return
        logo.setImageBitmap(state.artwork)
        name.text = state.title ?: "Radio Maroc & Coran"
        genre.text = state.subtitle.orEmpty()
        genre.visibility = if (state.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        strip.text = listOfNotNull(state.title, state.subtitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
        live.visibility = if (state.playing) View.VISIBLE else View.INVISIBLE
        bars.visibility = if (state.playing) View.VISIBLE else View.INVISIBLE
        bars.playing = state.playing
        play.setImageResource(if (state.playing) R.drawable.ic_home_pause else R.drawable.ic_home_play)
        play.contentDescription = if (state.playing) "Pause" else "Écouter"
    }
}

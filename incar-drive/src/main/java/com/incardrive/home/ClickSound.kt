package com.incardrive.home

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.audiofx.AudioEffect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs

/**
 * The soft click of Radio Maroc & Coran on every tap, with the radio's own sample. One short sound, played through
 * the media stream at the radio's level, only on a real tap of something that reacts (not a swipe).
 */
internal class ClickSound(private val context: Context) {
    private var pool: SoundPool? = null
    private var sample = 0
    private var ready = false
    private var downX = 0f
    private var downY = 0f
    private var target: View? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    var enabled = false
        set(value) {
            field = value
            if (value) prepare()
        }

    private fun prepare() {
        if (pool != null) return
        runCatching {
            val created = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            created.setOnLoadCompleteListener { _, id, status -> if (id == sample && status == 0) ready = true }
            sample = created.load(context.applicationContext, R.raw.home_click, 1)
            pool = created
        }
    }

    fun play() {
        if (enabled && ready) pool?.play(sample, .72f, .72f, 1, 0, 1f)
    }

    /** Called with every touch of the screen: a tap on a button or card gets the click when the finger lifts. */
    fun onTouch(root: View, event: MotionEvent) {
        if (!enabled) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                target = clickableAt(root, event.rawX.toInt(), event.rawY.toInt())
            }
            MotionEvent.ACTION_UP -> {
                if (target != null && abs(event.rawX - downX) < slop && abs(event.rawY - downY) < slop) play()
                target = null
            }
            MotionEvent.ACTION_CANCEL -> target = null
        }
    }

    private fun clickableAt(view: View, x: Int, y: Int): View? {
        if (view.visibility != View.VISIBLE || view.alpha == 0f) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (x < location[0] || y < location[1] || x >= location[0] + view.width || y >= location[1] + view.height) return null
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) clickableAt(view.getChildAt(index), x, y)?.let { return it }
        }
        return view.takeIf { it.isClickable || it.isLongClickable }
    }

    fun release() {
        pool?.release()
        pool = null
        ready = false
    }

    companion object {
        /** The head unit's own sound effects panel (equaliser, Dolby…), when one is installed. */
        fun equalizerIntent(context: Context): Intent? =
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                .takeIf { it.resolveActivity(context.packageManager) != null }
    }
}

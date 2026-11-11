package com.autodrive.radiomaroc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

internal class WeatherIcon(context: Context, tint: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = 1.65f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val cloud = Path().apply {
        moveTo(6.5f, 17f)
        cubicTo(1f, 17f, 1f, 9f, 6.5f, 9f)
        cubicTo(8f, 2.8f, 16.8f, 2.8f, 18.4f, 10f)
        cubicTo(23.3f, 10.2f, 23.1f, 17f, 18.5f, 17f)
        close()
    }
    private val moon = Path().apply {
        moveTo(19.9f, 15.1f)
        cubicTo(15.2f, 15.7f, 8.7f, 11.4f, 9.1f, 4.1f)
        cubicTo(1.5f, 6.7f, 2.2f, 17.1f, 8.8f, 19.5f)
        cubicTo(13.3f, 21.2f, 17.6f, 19.4f, 19.9f, 15.1f)
        close()
    }
    private val lightning = Path().apply {
        moveTo(13f, 16f)
        lineTo(9.8f, 19.7f)
        lineTo(13f, 19.7f)
        lineTo(11.4f, 22.7f)
    }
    private var condition = WeatherCondition.UNKNOWN
    private var night = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(weather: RadioWeather?) {
        condition = weather?.condition ?: WeatherCondition.UNKNOWN
        night = weather?.isDay == false
        visibility = if (condition == WeatherCondition.UNKNOWN) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        canvas.save()
        canvas.translate((width - side) / 2, (height - side) / 2)
        canvas.scale(side / 24f, side / 24f)
        when (condition) {
            WeatherCondition.CLEAR -> drawClear(canvas)
            WeatherCondition.PARTLY_CLOUDY -> {
                canvas.save()
                canvas.scale(.57f, .57f)
                drawClear(canvas)
                canvas.restore()
                canvas.save()
                canvas.translate(3.2f, 5.2f)
                canvas.scale(.87f, .87f)
                canvas.drawPath(cloud, paint)
                canvas.restore()
            }
            WeatherCondition.CLOUDY -> {
                canvas.translate(0f, 1.5f)
                canvas.drawPath(cloud, paint)
            }
            WeatherCondition.FOG, WeatherCondition.DRIZZLE, WeatherCondition.RAIN,
            WeatherCondition.SNOW, WeatherCondition.SHOWERS, WeatherCondition.STORM -> drawCloudWeather(canvas)
            WeatherCondition.UNKNOWN -> Unit
        }
        canvas.restore()
    }

    private fun drawClear(canvas: Canvas) {
        if (night) {
            canvas.drawPath(moon, paint)
        } else {
            canvas.drawCircle(12f, 12f, 4.2f, paint)
            canvas.save()
            repeat(8) {
                canvas.drawLine(12f, 2.5f, 12f, 5f, paint)
                canvas.rotate(45f, 12f, 12f)
            }
            canvas.restore()
        }
    }

    private fun drawCloudWeather(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, -2.5f)
        canvas.drawPath(cloud, paint)
        canvas.restore()
        when (condition) {
            WeatherCondition.FOG -> {
                canvas.drawLine(5f, 18f, 19f, 18f, paint)
                canvas.drawLine(8f, 21f, 16f, 21f, paint)
            }
            WeatherCondition.SNOW -> {
                canvas.drawCircle(7.5f, 18f, .6f, paint)
                canvas.drawCircle(12f, 21f, .6f, paint)
                canvas.drawCircle(16.5f, 18f, .6f, paint)
            }
            WeatherCondition.STORM -> canvas.drawPath(lightning, paint)
            else -> {
                canvas.drawLine(8.5f, 17f, 6.5f, 21f, paint)
                canvas.drawLine(14f, 17f, 12f, 21f, paint)
                if (condition != WeatherCondition.DRIZZLE) canvas.drawLine(19.5f, 17f, 17.5f, 21f, paint)
            }
        }
    }
}

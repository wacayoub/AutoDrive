package com.autodrive.launcher

import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.webkit.WebView

/** Display-only night adaptation, not Google's native map styling or a change to map data.
 * Apply to the complete WebView so image tiles, canvas, labels and provider controls stay consistent.
 * A stable light browser theme prevents combining the transform with a second provider dark theme.
 */
internal object MapDisplayTheme {
    fun apply(web: WebView) {
        val night = web.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (!night) {
            web.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        // Invert luminance and rotate hue 180 degrees, soften contrast and add a navy black point.
        val matrix = ColorMatrix(floatArrayOf(
            .4879f, -1.2155f, -.1224f, 0f, 228.75f,
            -.3621f, -.3655f, -.1224f, 0f, 239.75f,
            -.3621f, -1.2155f, .7276f, 0f, 250.75f,
            0f, 0f, 0f, 1f, 0f
        ))
        web.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
    }
}

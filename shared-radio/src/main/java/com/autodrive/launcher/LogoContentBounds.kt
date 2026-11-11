package com.autodrive.launcher

internal data class LogoContentBounds(val left: Int, val top: Int, val width: Int, val height: Int) {
    companion object {
        /** Crop only transparent padding or a white matte identified at every corner. */
        fun find(pixels: IntArray, width: Int, height: Int): LogoContentBounds? {
            val corners = intArrayOf(pixels[0], pixels[width - 1], pixels[(height - 1) * width], pixels.last())
            val transparentMatte = corners.any { (it ushr 24) <= 4 }
            if (!transparentMatte && !corners.all(::isWhiteMatte)) return null

            var left = width
            var top = height
            var right = 0
            var bottom = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val empty = if (transparentMatte) (pixel ushr 24) <= 4 else isWhiteMatte(pixel)
                    if (empty) continue
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x + 1)
                    bottom = maxOf(bottom, y + 1)
                }
            }
            if (right <= left || bottom <= top || (left == 0 && top == 0 && right == width && bottom == height)) return null
            return LogoContentBounds(left, top, right - left, bottom - top)
        }

        private fun isWhiteMatte(pixel: Int): Boolean = (pixel ushr 24) >= 252 &&
            (pixel ushr 16 and 0xff) >= 248 && (pixel ushr 8 and 0xff) >= 248 && (pixel and 0xff) >= 248
    }
}

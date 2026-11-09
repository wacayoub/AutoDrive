package com.autodrive.launcher

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun InputStream.readUpTo(limit: Int): ByteArray {
    val buffer = ByteArray(minOf(limit, 8 * 1024))
    val result = ByteArrayOutputStream(buffer.size)
    while (result.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - result.size()))
        if (count == -1) break
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}

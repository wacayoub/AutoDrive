package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrowShapeTest {
    /** A grid from a drawing: '#' is the arrow. */
    private fun turn(vararg rows: String): Turn? {
        val width = rows.maxOf { it.length }
        val grid = BooleanArray(width * rows.size) { rows[it / width].getOrNull(it % width) == '#' }
        return ArrowShape.turn(grid, width, rows.size)
    }

    @Test fun aTurnLeftArrow() = assertEquals(Turn.LEFT, turn(
        "  #         ",
        " ##         ",
        "########### ",
        " ##      ## ",
        "  #      ## ",
        "         ## ",
        "         ## ",
        "         ## ",
        "         ## ",
        "         ## ",
    ))

    @Test fun aTurnRightArrow() = assertEquals(Turn.RIGHT, turn(
        "         #  ",
        "         ## ",
        " ###########",
        " ##      ## ",
        " ##      #  ",
        " ##         ",
        " ##         ",
        " ##         ",
        " ##         ",
        " ##         ",
    ))

    @Test fun aStraightArrow() = assertEquals(Turn.STRAIGHT, turn(
        "     ##     ",
        "    ####    ",
        "   ######   ",
        "  ########  ",
        "     ##     ",
        "     ##     ",
        "     ##     ",
        "     ##     ",
        "     ##     ",
        "     ##     ",
    ))

    @Test fun aSlightLeftArrow() = assertEquals(Turn.LEFT, turn(
        " #####      ",
        " ####       ",
        " ## ##      ",
        " #   ##     ",
        "      ##    ",
        "       ##   ",
        "       ##   ",
        "       ##   ",
        "       ##   ",
        "       ##   ",
    ))

    @Test fun aUTurnArrow() = assertEquals(Turn.UTURN, turn(
        "   ######   ",
        "  ########  ",
        " ##      ## ",
        " ##      ## ",
        " ##      ## ",
        " ##      ## ",
        "####     ## ",
        " ##      ## ",
        " #       ## ",
        "         ## ",
    ))

    @Test fun nothingToRead() {
        assertNull(turn("            ", "            "))
        assertNull(turn("  ##  ", "  ##  "))
    }
}

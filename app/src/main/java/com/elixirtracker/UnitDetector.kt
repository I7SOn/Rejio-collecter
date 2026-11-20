package com.elixirtracker

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

class UnitDetector {

    companion object {
        const val ARENA_TOP_FRAC = 0.18f
        const val ARENA_BOTTOM_FRAC = 0.62f
        const val ARENA_LEFT_FRAC = 0.10f
        const val ARENA_RIGHT_FRAC = 0.90f
        const val CELL_SIZE = 24
        const val CHANGE_PIXEL_DIFF = 40
        const val CHANGE_CELL_RATIO = 0.35f
    }

    private var previousFrame: Bitmap? = null

    data class Detection(
        val cx: Int,
        val cy: Int,
        val widthCells: Int,
        val heightCells: Int,
        val intensity: Float
    )

    fun detect(current: Bitmap): List<Detection> {
        val prev = previousFrame
        if (prev == null) {
            previousFrame = current
            return emptyList()
        }

        val w = current.width
        val h = current.height
        val top = (h * ARENA_TOP_FRAC).toInt()
        val bottom = (h * ARENA_BOTTOM_FRAC).toInt()
        val left = (w * ARENA_LEFT_FRAC).toInt()
        val right = (w * ARENA_RIGHT_FRAC).toInt()

        val cols = (right - left) / CELL_SIZE
        val rows = (bottom - top) / CELL_SIZE
        if (cols <= 0 || rows <= 0) return emptyList()

        val changed = Array(rows) { BooleanArray(cols) }
        val intensity = Array(rows) { FloatArray(cols) }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = left + c * CELL_SIZE
                val y0 = top + r * CELL_SIZE
                var diffPx = 0
                var totalPx = 0
                for (dy in 0 until CELL_SIZE step 2) {
                    for (dx in 0 until CELL_SIZE step 2) {
                        val x = x0 + dx
                        val y = y0 + dy
                        if (x >= w || y >= h) continue
                        totalPx++
                        val a = prev.getPixel(x, y)
                        val b = current.getPixel(x, y)
                        val d = abs(Color.red(a) - Color.red(b)) +
                                abs(Color.green(a) - Color.green(b)) +
                                abs(Color.blue(a) - Color.blue(b))
                        if (d > CHANGE_PIXEL_DIFF * 3) diffPx++
                    }
                }
                if (totalPx > 0) {
                    val ratio = diffPx.toFloat() / totalPx
                    intensity[r][c] = ratio
                    changed[r][c] = ratio > CHANGE_CELL_RATIO
                }
            }
        }

        val detections = findClusters(changed, intensity, left, top)
        previousFrame = current
        return detections
    }

    private fun findClusters(
        changed: Array<BooleanArray>,
        intensity: Array<FloatArray>,
        offsetX: Int,
        offsetY: Int
    ): List<Detection> {
        val rows = changed.size
        val cols = changed[0].size
        val visited = Array(rows) { BooleanArray(cols) }
        val result = mutableListOf<Detection>()

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!changed[r][c] || visited[r][c]) continue

                var minR = r; var maxR = r; var minC = c; var maxC = c
                var sumInt = 0f; var count = 0
                val stack = ArrayDeque<Pair<Int, Int>>()
                stack.addLast(r to c)

                while (stack.isNotEmpty()) {
                    val (rr, cc) = stack.removeLast()
                    if (rr < 0 || rr >= rows || cc < 0 || cc >= cols) continue
                    if (visited[rr][cc] || !changed[rr][cc]) continue
                    visited[rr][cc] = true
                    minR = minOf(minR, rr); maxR = maxOf(maxR, rr)
                    minC = minOf(minC, cc); maxC = maxOf(maxC, cc)
                    sumInt += intensity[rr][cc]; count++

                    for (dr in -1..1) for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        stack.addLast((rr + dr) to (cc + dc))
                    }
                }

                val hCells = maxR - minR + 1
                val wCells = maxC - minC + 1
                if (wCells < 2 || hCells < 2) continue
                if (wCells > cols / 2 || hCells > rows / 2) continue

                val cx = offsetX + (minC + maxC + 1) * CELL_SIZE / 2
                val cy = offsetY + (minR + maxR + 1) * CELL_SIZE / 2
                result.add(Detection(cx, cy, wCells, hCells, sumInt / count))
            }
        }

        return result
    }

    fun reset() {
        previousFrame = null
    }
}

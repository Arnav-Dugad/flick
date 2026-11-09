package io.github.arnavdugad.flick.action

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import io.github.arnavdugad.flick.core.model.Direction

/**
 * Scrolls the largest visible scrollable view using accessibility actions. This is smoother and
 * more precise than a swipe in readers and browsers. Returns false when nothing could scroll so the
 * caller can fall back to a swipe gesture.
 */
object NodeScroller {

    private const val MAX_NODES = 600

    fun scroll(root: AccessibilityNodeInfo?, direction: Direction): Boolean {
        val target = root?.let(::findLargestScrollable) ?: return false
        val directional = when (direction) {
            Direction.DOWN -> AccessibilityAction.ACTION_SCROLL_DOWN
            Direction.UP -> AccessibilityAction.ACTION_SCROLL_UP
            Direction.LEFT -> AccessibilityAction.ACTION_SCROLL_LEFT
            Direction.RIGHT -> AccessibilityAction.ACTION_SCROLL_RIGHT
        }
        if (target.actionList.any { it.id == directional.id } && target.performAction(directional.id)) {
            return true
        }
        val generic = when (direction) {
            Direction.DOWN, Direction.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            Direction.UP, Direction.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return target.performAction(generic)
    }

    private fun findLargestScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val bounds = Rect()
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable && node.isVisibleToUser) {
                node.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height().toLong()
                if (area > bestArea) {
                    best = node
                    bestArea = area
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return best
    }
}

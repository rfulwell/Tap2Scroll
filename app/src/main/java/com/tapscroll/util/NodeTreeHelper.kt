package com.tapscroll.util

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Helper class for working with accessibility node trees
 * Used to detect interactive elements that should not trigger scrolling
 */
class NodeTreeHelper {

    companion object {
        // Class names of known interactive elements
        private val INTERACTIVE_CLASS_NAMES = setOf(
            "android.widget.Button",
            "android.widget.ImageButton",
            "android.widget.EditText",
            "android.widget.CheckBox",
            "android.widget.RadioButton",
            "android.widget.Switch",
            "android.widget.ToggleButton",
            "android.widget.Spinner",
            "android.widget.SeekBar",
            "android.widget.RatingBar",
            "androidx.appcompat.widget.AppCompatButton",
            "androidx.appcompat.widget.AppCompatEditText",
            "androidx.appcompat.widget.AppCompatCheckBox",
            "com.google.android.material.button.MaterialButton",
            "com.google.android.material.textfield.TextInputEditText"
        )

        // Role descriptions that indicate interactivity (for web content)
        private val INTERACTIVE_ROLES = setOf(
            "link",
            "button",
            "checkbox",
            "radio",
            "textbox",
            "searchbox",
            "combobox",
            "listbox",
            "menu",
            "menuitem",
            "tab",
            "switch",
            "slider"
        )
    }

    /**
     * Check if a node or any of its ancestors is interactive
     * 
     * @param node The accessibility node to check
     * @param maxDepth Maximum depth to traverse upward (prevents infinite loops)
     * @return true if the node or an ancestor is interactive
     */
    fun isInteractiveElement(node: AccessibilityNodeInfo?, maxDepth: Int = 10): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0

        while (current != null && depth < maxDepth) {
            if (isNodeInteractive(current)) {
                return true
            }
            
            val parent = current.parent
            if (current != node) {
                current.recycle()
            }
            current = parent
            depth++
        }

        // Clean up the last node if it's not the original
        if (current != null && current != node) {
            current.recycle()
        }

        return false
    }

    /**
     * Check if a single node is interactive
     */
    private fun isNodeInteractive(node: AccessibilityNodeInfo): Boolean {
        // Check basic interactive properties
        if (node.isClickable || node.isLongClickable || node.isCheckable) {
            return true
        }

        // Check if it's editable (text input)
        if (node.isEditable) {
            return true
        }

        // Check if it has click action
        if (node.actionList.any { 
            it.id == AccessibilityNodeInfo.ACTION_CLICK || 
            it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK 
        }) {
            return true
        }

        // Check class name against known interactive types
        val className = node.className?.toString() ?: ""
        if (INTERACTIVE_CLASS_NAMES.any { className.contains(it, ignoreCase = true) }) {
            return true
        }

        // Check role description for web content
        val roleDescription = AccessibilityNodeInfoCompat.wrap(node).roleDescription?.toString()?.lowercase() ?: ""
        if (INTERACTIVE_ROLES.contains(roleDescription)) {
            return true
        }

        // Additional heuristics for web content
        // Links often have contentDescription or text that looks like a URL or "click here"
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        
        // Check for common link patterns
        if (contentDesc.startsWith("http") || text.startsWith("http")) {
            return true
        }

        return false
    }

    /**
     * Find a node at specific screen coordinates
     * 
     * @param rootNode The root node to search from
     * @param x Screen x coordinate
     * @param y Screen y coordinate
     * @return The node at those coordinates, or null if not found
     */
    fun findNodeAtCoordinates(
        rootNode: AccessibilityNodeInfo?,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        if (rootNode == null) return null

        val rect = android.graphics.Rect()
        rootNode.getBoundsInScreen(rect)

        // Check if coordinates are within this node
        if (!rect.contains(x, y)) {
            return null
        }

        // Search children for a more specific match
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val childMatch = findNodeAtCoordinates(child, x, y)
            if (childMatch != null) {
                if (child != childMatch) {
                    child.recycle()
                }
                return childMatch
            }
            child.recycle()
        }

        // No child contains the point, so this node is the best match
        return rootNode
    }

    /**
     * Get a simple description of a node for debugging
     */
    fun describeNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return "null"
        
        return buildString {
            append("class=${node.className}")
            node.text?.let { append(", text='${it.take(20)}'") }
            node.contentDescription?.let { append(", desc='${it.take(20)}'") }
            append(", clickable=${node.isClickable}")
            append(", editable=${node.isEditable}")
        }
    }
}

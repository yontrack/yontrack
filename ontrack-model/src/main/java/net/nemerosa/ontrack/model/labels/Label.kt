package net.nemerosa.ontrack.model.labels

import net.nemerosa.ontrack.common.toRGBColor

open class Label(
    val id: Int,
    val category: String?,
    val name: String,
    val description: String?,
    val color: String,
) {
    /**
     * Foreground colour
     */
    val foregroundColor: String get() = color.toRGBColor().toBlackOrWhite().toString()

    /**
     * Representation
     */
    fun getDisplay() = if (category != null) {
        "$category:$name"
    } else {
        name
    }

    companion object {
        fun categoryAndNameFromDisplay(display: String): Pair<String?, String> {
            val index = display.indexOf(':')
            return if (index >= 0) {
                val category = display.substring(0, index).trim().takeIf { it.isNotBlank() }
                val name = display.substring(index + 1).trim()
                category to name
            } else {
                null to display
            }
        }
    }
}

package net.nemerosa.ontrack.common

import kotlin.math.pow

/**
 * Regular expression to validate a colour.
 */
const val RGB_COLOR_REGEX: String = "#([a-fA-F0-9]{2})([a-fA-F0-9]{2})([a-fA-F0-9]{2})"

data class RGBColor(
        val red: Int,
        val green: Int,
        val blue: Int
) {

    init {
        validateComponent(red)
        validateComponent(green)
        validateComponent(blue)
    }

    private fun validateComponent(component: Int) {
        if (component < 0) throw RGBColorException("Color component must be >= 0")
        if (component > 255) throw RGBColorException("Color component must be <= 255")
    }

    /**
     * WCAG 2.x relative luminance of the colour, between 0.0 (black) and 1.0 (white).
     *
     * This is perceived brightness, not HSL lightness: each channel is linearised out of sRGB's
     * gamma encoding, then weighted by how much the eye actually gets from it - green far more
     * than red, red far more than blue. A saturated colour has a low HSL lightness however bright
     * it looks (`#FFEB3B` yellow sits at 0.615), which is why lightness is the wrong quantity to
     * decide a foreground colour on.
     *
     * Ref: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
     */
    fun relativeLuminance(): Double =
        0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)

    /**
     * WCAG 2.x contrast ratio between this colour and [other], between 1.0 (identical luminance)
     * and 21.0 (black against white).
     *
     * Ref: https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
     */
    fun contrastRatio(other: RGBColor): Double {
        val a = relativeLuminance()
        val b = other.relativeLuminance()
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Returns black, or white, whichever contrasts better against this colour - used as the
     * foreground colour for text drawn on top of it, such as a label chip.
     *
     * The choice is the WCAG contrast ratio itself rather than a hand-picked brightness threshold:
     * whichever of the two the standard scores higher wins, which puts the crossover at a relative
     * luminance of about 0.179. Ties go to black.
     */
    fun toBlackOrWhite(): RGBColor =
        if (contrastRatio(BLACK) >= contrastRatio(WHITE)) BLACK else WHITE

    override fun toString(): String {
        return "#${toHex(red)}${toHex(green)}${toHex(blue)}"
    }

    companion object {
        /**
         * Regex
         */
        private val regex = RGB_COLOR_REGEX.toRegex()

        /**
         * Black
         */
        val BLACK = RGBColor(0, 0, 0)

        /**
         * White
         */
        val WHITE = RGBColor(255, 255, 255)

        /**
         * Undoes the sRGB gamma encoding of one 0..255 channel, giving the linear 0.0..1.0 value
         * the relative luminance weights apply to.
         */
        private fun linearize(component: Int): Double {
            val c = component / 255.0
            return if (c <= 0.04045) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).pow(2.4)
            }
        }

        /**
         *
         */
        fun parse(text: String): RGBColor {
            val matchResult = regex.matchEntire(text)
            if (matchResult != null) {
                val (hr, hg, hb) = matchResult.destructured
                val r = fromHex(hr)
                val g = fromHex(hg)
                val b = fromHex(hb)
                return RGBColor(r, g, b)
            } else {
                throw RGBColorException("Color format does not match: $text")
            }
        }

        private fun fromHex(hex: String): Int {
            return hex.toInt(16)
        }

        private fun toHex(component: Int): String {
            val h = component.toString(16).uppercase()
            return if (component < 16) {
                "0$h"
            } else {
                h
            }
        }
    }

}

fun String.toRGBColor() = RGBColor.parse(this)

class RGBColorException(message: String) : IllegalArgumentException(message)

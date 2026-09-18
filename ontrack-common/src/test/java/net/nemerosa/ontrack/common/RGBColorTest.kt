package net.nemerosa.ontrack.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RGBColorTest {

    @Test
    fun parsing() {
        "#ff1111" convertsTo RGBColor(255, 17, 17)
        "#FF1111" convertsTo RGBColor(255, 17, 17)
        "ff1111".doesNotConvert()
        "f11".doesNotConvert()
        "white".doesNotConvert()
    }

    @Test
    fun `Relative luminance of the extremes`() {
        assertEquals(0.0, RGBColor.BLACK.relativeLuminance(), 1e-9)
        assertEquals(1.0, RGBColor.WHITE.relativeLuminance(), 1e-9)
    }

    @Test
    fun `Relative luminance weighs green above red above blue`() {
        val red = "#ff0000".toRGBColor().relativeLuminance()
        val green = "#00ff00".toRGBColor().relativeLuminance()
        val blue = "#0000ff".toRGBColor().relativeLuminance()
        assertTrue(blue < red, "blue ($blue) is dimmer than red ($red)")
        assertTrue(red < green, "red ($red) is dimmer than green ($green)")
    }

    @Test
    fun `Black and white on the extremes`() {
        RGBColor.BLACK blackAndWhiteTo RGBColor.WHITE
        RGBColor.WHITE blackAndWhiteTo RGBColor.BLACK
    }

    /**
     * These are the ones the old lightness-based implementation got wrong: a saturated colour has
     * a low HSL lightness however bright it looks, so bright yellow, cyan and green were all given
     * white text. See issue #1813.
     */
    @Test
    fun `Black and white on bright saturated colors`() {
        "#ffeb3b" blackAndWhiteTo RGBColor.BLACK // Material yellow, the one reported
        "#ffff00" blackAndWhiteTo RGBColor.BLACK // Pure yellow
        "#00ffff" blackAndWhiteTo RGBColor.BLACK // Pure cyan
        "#00ff00" blackAndWhiteTo RGBColor.BLACK // Pure green
        "#11ff11" blackAndWhiteTo RGBColor.BLACK // Near-pure green
        "#ff1111" blackAndWhiteTo RGBColor.BLACK // Near-pure red
    }

    @Test
    fun `Black and white on dark colors`() {
        "#1111ff" blackAndWhiteTo RGBColor.WHITE
        "#0f44f8" blackAndWhiteTo RGBColor.WHITE
        "#000080" blackAndWhiteTo RGBColor.WHITE // Navy
        "#001111" blackAndWhiteTo RGBColor.WHITE
        "#110011" blackAndWhiteTo RGBColor.WHITE
        "#111100" blackAndWhiteTo RGBColor.WHITE
    }

    /**
     * The band where white scores between 3:1 and AA's 4.5:1 and black scores a little better.
     * Both are legible here, and the convention in most tag UIs is white - white on red, white on
     * the picker's default blue. The deliberate decision (issue #1813) was to follow WCAG anyway
     * and hand these to black, rather than carve out a hue exception or a hand-picked floor.
     *
     * `#1677FF` is `defaultColor` in `LabelDialog`, so it is what every label created without
     * touching the colour picker gets. Its flip to black text is expected, not a regression.
     */
    @Test
    fun `Black and white on the debatable band is decided by WCAG, not by convention`() {
        "#ff4d4f" blackAndWhiteTo RGBColor.BLACK // Ant red-5
        "#eb2f96" blackAndWhiteTo RGBColor.BLACK // Ant magenta-6
        "#f5222d" blackAndWhiteTo RGBColor.BLACK // Ant red-6
        "#1677ff" blackAndWhiteTo RGBColor.BLACK // The label colour picker's default
    }

    @Test
    fun `Black and white on pale colors`() {
        "#e0f6e4" blackAndWhiteTo RGBColor.BLACK
    }

    @Test
    fun `Black and white on greys`() {
        "#333333" blackAndWhiteTo RGBColor.WHITE
        "#666666" blackAndWhiteTo RGBColor.WHITE
        "#808080" blackAndWhiteTo RGBColor.BLACK
        "#999999" blackAndWhiteTo RGBColor.BLACK
    }

    /**
     * Whichever of black and white is returned must be the one with the better WCAG contrast
     * ratio - that is the whole contract of the method.
     */
    @Test
    fun `Black and white always picks the better contrast`() {
        listOf(
            "#ffeb3b", "#ff1111", "#11ff11", "#1111ff", "#0f44f8", "#e0f6e4",
            "#000000", "#ffffff", "#808080", "#666666", "#00ffff", "#000080",
        ).forEach { hex ->
            val color = hex.toRGBColor()
            val chosen = color.toBlackOrWhite()
            val other = if (chosen == RGBColor.BLACK) RGBColor.WHITE else RGBColor.BLACK
            val chosenContrast = color.contrastRatio(chosen)
            val otherContrast = color.contrastRatio(other)
            assertTrue(
                chosenContrast >= otherContrast,
                "$hex chose $chosen (contrast $chosenContrast) over $other (contrast $otherContrast)"
            )
        }
    }

    private fun String.doesNotConvert() {
        assertFailsWith<RGBColorException> {
            toRGBColor()
        }
    }

    private infix fun String.convertsTo(expected: RGBColor) {
        assertEquals(
                expected,
                toRGBColor(),
                "$this converts to RGBColor $expected"
        )
    }

    private infix fun String.blackAndWhiteTo(expected: RGBColor) {
        RGBColor.parse(this) blackAndWhiteTo expected
    }

    private infix fun RGBColor.blackAndWhiteTo(expected: RGBColor) {
        assertEquals(
                expected,
                this.toBlackOrWhite(),
                "$this in B/W is expected to be $expected"
        )
    }

}

import {getTextColorForBackground, numberToColorHsl} from "@components/common/colors/Colors";

/**
 * WCAG 2.x relative luminance, recomputed here on purpose: the test must not
 * ask the implementation whether it agrees with itself.
 *
 * Ref: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
const relativeLuminance = (hexColor) => {
    const linearize = (component) => {
        const c = component / 255
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
    }
    const r = parseInt(hexColor.slice(1, 3), 16)
    const g = parseInt(hexColor.slice(3, 5), 16)
    const b = parseInt(hexColor.slice(5, 7), 16)
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

const contrastRatio = (hexColor, foreground) => {
    const a = relativeLuminance(hexColor)
    const b = relativeLuminance(foreground)
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

const otherOf = (foreground) => foreground === '#000000' ? '#FFFFFF' : '#000000'

describe('Colors', () => {

    it('generate colors', () => {
        const inputNumber = 12345
        const bgColor = numberToColorHsl(inputNumber)
        const textColor = getTextColorForBackground(bgColor)

        expect(bgColor).toEqual("#66cc7d")
        expect(textColor).toEqual("#000000")
    })

    it('generates a stable hue for a given number', () => {
        expect(numberToColorHsl(0)).toEqual("#cc6666")
        expect(numberToColorHsl(198)).toEqual("#667dcc")
        expect(numberToColorHsl(12345)).toEqual("#66cc7d")
    })

    describe('getTextColorForBackground', () => {

        /**
         * The same cases `RGBColorTest` pins on the backend - the two
         * implementations of this decision must not drift apart again.
         */
        it('agrees with the backend on the colours RGBColorTest pins', () => {
            expect(getTextColorForBackground("#ffeb3b")).toEqual('#000000') // bright yellow
            expect(getTextColorForBackground("#e0f6e4")).toEqual('#000000') // pale green
            expect(getTextColorForBackground("#333333")).toEqual('#FFFFFF')
            expect(getTextColorForBackground("#666666")).toEqual('#FFFFFF')
            expect(getTextColorForBackground("#808080")).toEqual('#000000')
            expect(getTextColorForBackground("#999999")).toEqual('#000000')
            expect(getTextColorForBackground("#000000")).toEqual('#FFFFFF')
            expect(getTextColorForBackground("#ffffff")).toEqual('#000000')
        })

        /**
         * The 53 generated hues out of 720 where the WCAG rule and the NTSC
         * brightness approximation it replaced disagree: white scores about
         * 4.0-4.6:1 on these, black slightly better. The old rule returned
         * white for every one of them.
         */
        it.each([
            ["#8c66cc", 15],
            ["#6677cc", 20],
            ["#8466cc", 49],
            ["#9166cc", 70],
            ["#6672cc", 75],
            ["#7c66cc", 83],
            ["#8966cc", 104],
            ["#667acc", 109],
            ["#667dcc", 198],
        ])('picks black on %s, in the band where the old rule picked white', (hex, colorIndex) => {
            expect(numberToColorHsl(colorIndex)).toEqual(hex)
            expect(getTextColorForBackground(hex)).toEqual('#000000')
        })

        it('picks black over white on the worst generated colour, lifting it above AA', () => {
            // #667dcc, colour index 198, is the worst of the 720 generated
            // hues: the old rule chose white there, at 3.91:1.
            const worst = numberToColorHsl(198)
            expect(contrastRatio(worst, '#FFFFFF')).toBeLessThan(4.5)
            expect(getTextColorForBackground(worst)).toEqual('#000000')
            expect(contrastRatio(worst, '#000000')).toBeGreaterThan(4.5)
        })

        it('always returns whichever of black and white contrasts better', () => {
            for (let colorIndex = 0; colorIndex < 720; colorIndex++) {
                const bgColor = numberToColorHsl(colorIndex)
                const chosen = getTextColorForBackground(bgColor)
                expect(contrastRatio(bgColor, chosen))
                    .toBeGreaterThanOrEqual(contrastRatio(bgColor, otherOf(chosen)))
            }
        })

        /**
         * With the foreground chosen by WCAG, every generated icon clears AA
         * for normal text at the current saturation 50% / lightness 60%. This
         * is what makes a change to `numberToColorHsl` unnecessary.
         */
        it('clears WCAG AA on every generated colour', () => {
            for (let colorIndex = 0; colorIndex < 720; colorIndex++) {
                const bgColor = numberToColorHsl(colorIndex)
                const chosen = getTextColorForBackground(bgColor)
                expect(contrastRatio(bgColor, chosen)).toBeGreaterThanOrEqual(4.5)
            }
        })

        it('accepts upper case hex', () => {
            expect(getTextColorForBackground("#667DCC")).toEqual('#000000')
            expect(getTextColorForBackground("#FFEB3B")).toEqual('#000000')
        })
    })
})

export function numberToColorHsl(num) {
    // Map number to a hue
    const hue = (num * 137.50776405003785) % 360;
    const saturation = 50; // 50%
    const lightness = 60;  // 60%

    return hslToHex(hue, saturation, lightness);
}

function hslToHex(h, s, l) {
    // Convert HSL to values in [0,1]
    s /= 100;
    l /= 100;

    const k = n => (n + h / 30) % 12;
    const a = s * Math.min(l, 1 - l);
    const f = n =>
        l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));

    const r = Math.round(f(0) * 255);
    const g = Math.round(f(8) * 255);
    const b = Math.round(f(4) * 255);

    return rgbToHex(r, g, b);
}

function rgbToHex(r, g, b) {
    const toHex = x => x.toString(16).padStart(2, '0');
    return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

/**
 * Undoes the sRGB gamma encoding of one 0..255 channel, giving the linear
 * 0.0..1.0 value the relative luminance weights apply to.
 */
function linearize(component) {
    const c = component / 255;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

/**
 * WCAG 2.x relative luminance of a `#rrggbb` colour, between 0.0 (black) and
 * 1.0 (white).
 *
 * This is perceived brightness, not HSL lightness: each channel is linearised
 * out of sRGB's gamma encoding, then weighted by how much the eye actually gets
 * from it - green far more than red, red far more than blue.
 *
 * Ref: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
function relativeLuminance(hexColor) {
    const r = parseInt(hexColor.slice(1, 3), 16);
    const g = parseInt(hexColor.slice(3, 5), 16);
    const b = parseInt(hexColor.slice(5, 7), 16);
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b);
}

/**
 * WCAG 2.x contrast ratio between two relative luminances, between 1.0
 * (identical) and 21.0 (black against white).
 *
 * Ref: https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 */
function contrastRatio(a, b) {
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

/**
 * Returns black, or white, whichever contrasts better against `hexColor` - the
 * foreground colour for text drawn on top of it, such as a generated icon's
 * initials.
 *
 * The choice is the WCAG contrast ratio itself rather than a hand-picked
 * brightness threshold: whichever of the two the standard scores higher wins,
 * which puts the crossover at a relative luminance of about 0.179. Ties go to
 * black.
 *
 * `RGBColor.toBlackOrWhite()` in `ontrack-common` is the authority for this
 * rule and makes the same decision for `Label.foregroundColor`, which the
 * frontend reads over GraphQL rather than recomputing. This copy exists only
 * because a generated icon's hue is produced client-side by
 * `numberToColorHsl()` and never reaches the backend. Keep the two in step -
 * `RGBColorTest` and `Colors.test.js` pin the same cases on both sides.
 */
export function getTextColorForBackground(hexColor) {
    const l = relativeLuminance(hexColor);
    const black = contrastRatio(l, 0);
    const white = contrastRatio(l, 1);
    return black >= white ? '#000000' : '#FFFFFF';
}

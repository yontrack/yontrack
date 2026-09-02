// @ts-check

/**
 * The square box every small entity mark is drawn in.
 *
 * Extracted because three marks were each restating it: the initials tile, the
 * validation stamp's data-type glyph, and (predating them) the run status mark.
 * The rules it encodes are easy to get subtly wrong and expensive when they are:
 * a box that is not square lets a disc render as an ellipse, and a box a flex
 * parent is free to squash clips its own contents.
 *
 * Returns the geometry ONLY. Colour, radius and border stay with the caller,
 * because those are exactly what distinguishes one mark from another.
 *
 * @param {number} size Box size in px.
 * @returns {import('react').CSSProperties}
 */
export function iconBoxStyle(size) {
    return {
        // Flex centring rather than line-height: line-height drifts off-centre
        // as soon as the content is sized as a fraction of the box.
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxSizing: 'border-box',
        width: `${size}px`,
        height: `${size}px`,
        // Fixed basis, no grow, no shrink: a flex parent must not be able to
        // squash the box out of square.
        flex: `0 0 ${size}px`,
    }
}

import {getTextColorForBackground, numberToColorHsl} from "@components/common/colors/Colors";
import {theme, Tooltip} from "antd";
import {actionClassName} from "@components/common/ClassUtils";
import {iconBoxStyle} from "@components/common/icons/iconBox";

/**
 * The identity fallback for an entity that has no uploaded image: a coloured
 * tile carrying the entity's initials.
 *
 * This is drawn at 16px in dense tables and at 22-32px on promotion medals and
 * validation chips, so every dimension is derived from `size` rather than fixed.
 * The glyph is sized as a fraction of the box, floored so it never becomes
 * unreadable in the densest rows.
 *
 * Radius and type come from Ant Design tokens; the tile's own hue is generated
 * from `colorIndex` and is deliberately outside the token palette - the point of
 * that hue is to tell two entities apart, which a themed colour cannot do.
 *
 * @param {Object} props
 * @param {string} [props.id] Rendered as `data-testid`.
 * @param {string} [props.name] Initials are generated from it, and it is the
 *   accessible name. Nothing renders without it.
 * @param {number} [props.colorIndex] Seeds the background hue.
 * @param {() => void} [props.onClick]
 * @param {string} [props.tooltipText]
 * @param {number} [props.size] Box size in px.
 * @param {boolean} [props.disabled]
 */
export default function GeneratedIcon({id, name, colorIndex, onClick, tooltipText, size = 24, disabled = false}) {

    const {token} = theme.useToken()

    const initials = name && generateInitials(name)
    const bgColor = numberToColorHsl(colorIndex)
    const textColor = getTextColorForBackground(bgColor)

    // Two capitals at weight 600 run about 1.4x the font size wide, so 0.44 of
    // the box is the largest ratio that still leaves them room to breathe at
    // 22px. Floored at 9px: below that the pair stops being readable at all, and
    // a slightly cramped 12px tile beats an illegible one.
    const fontSize = Math.max(9, Math.round(size * 0.44))

    // Small tiles need the tighter radius or the corners eat into the letters;
    // the medals and chips can carry the standard one.
    const borderRadius = size >= 22 ? token.borderRadius : token.borderRadiusSM

    return (
        <>
            {
                name &&
                <Tooltip title={tooltipText}>
                <span
                    data-testid={id}
                    role="img"
                    aria-label={name}
                    style={{
                        ...iconBoxStyle(size),
                        backgroundColor: bgColor,
                        color: textColor,
                        fontFamily: token.fontFamily,
                        fontSize: `${fontSize}px`,
                        // Heavier than body text: the pair is small, sits on a
                        // saturated ground, and has no descenders to give it
                        // shape.
                        fontWeight: 600,
                        letterSpacing: '0.02em',
                        lineHeight: 1,
                        borderRadius: `${borderRadius}px`,
                        verticalAlign: 'middle',
                        userSelect: 'none',
                        filter: disabled ? 'grayscale(100%)' : undefined,
                    }}
                    onClick={onClick}
                    className={actionClassName(onClick, disabled)}
                >
                  {initials}
                </span>
                </Tooltip>
            }
        </>
    )
}

export const generateInitials = (name) => {
    const parts = name.split('-')

    // If there's only one part, use the first two letters of it
    if (parts.length === 1) {
        return parts[0].substring(0, 2).toUpperCase()
    }

    // If there are multiple parts, use the first letter of the first two parts
    return parts
        .slice(0, 2) // In case there are more than two parts, just take the first two
        .map(part => part.charAt(0).toUpperCase())
        .join('')
}

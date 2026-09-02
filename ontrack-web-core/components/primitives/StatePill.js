// @ts-check

import {theme} from "antd"
import Link from "next/link"

/**
 * A small status-coloured label.
 *
 * This is the shared primitive behind every "state as a short label" in the
 * product: the counts on a promotion medal's notification badges, and the run
 * state on a validation chip. It renders a label and nothing else - no counting,
 * no fetching, no layout decisions. Callers arrange it.
 *
 * COLOUR comes from Ant Design tokens read through `theme.useToken()`, never
 * from CSS custom properties: theming is owned elsewhere and a second token
 * system living in this component would end up owned by nobody. Callers that
 * have their own documented palette - the validation run statuses, whose hues
 * and contrast ratios live in `ValidationRunStatusConfig` - pass it in through
 * `colors` rather than adding entries to the table below.
 *
 * MOTION is opt-in through `pulse`, and is applied by adding the `ot-pulse`
 * class from `globals.css`. The animation itself is declared inside a
 * `@media (prefers-reduced-motion: no-preference)` guard, so a user who asked
 * for less motion never sees a frame of it. Do not reimplement the pulse as an
 * inline style here: an inline style cannot carry that guard.
 */

/**
 * The semantic states this pill knows how to paint.
 *
 * `neutral` is a real state, not an error case: "nothing notable" is a thing a
 * pill legitimately has to say, and it is also the fallback for an unrecognised
 * state so that a new backend value can never render an uncoloured pill.
 *
 * @type {ReadonlyArray<'success'|'processing'|'error'|'warning'|'neutral'>}
 */
export const PILL_STATES = ['success', 'processing', 'error', 'warning', 'neutral']

/** @typedef {typeof PILL_STATES[number]} PillState */

/**
 * @typedef {Object} PillColors
 * @property {string} color Text and glyph colour.
 * @property {string} background
 * @property {string} border
 */

/**
 * Maps a semantic state to the Ant Design tokens that paint it.
 *
 * Kept as a function of the token set rather than a constant table so it always
 * reflects the active algorithm - the dark theme swaps the token values, and
 * this follows without a second palette.
 *
 * @param {Record<string, any>} token The token set from `theme.useToken()`.
 * @returns {Record<PillState, PillColors>}
 */
const pillColorsFor = (token) => ({
    success: {
        color: token.colorSuccessText,
        background: token.colorSuccessBg,
        border: token.colorSuccessBorder,
    },
    // 'processing' rather than 'info': the state this paints is always "still
    // happening", and naming it for the meaning keeps callers from reaching for
    // a hue instead.
    processing: {
        color: token.colorInfoText,
        background: token.colorInfoBg,
        border: token.colorInfoBorder,
    },
    error: {
        color: token.colorErrorText,
        background: token.colorErrorBg,
        border: token.colorErrorBorder,
    },
    warning: {
        color: token.colorWarningText,
        background: token.colorWarningBg,
        border: token.colorWarningBorder,
    },
    neutral: {
        color: token.colorTextSecondary,
        background: token.colorFillQuaternary,
        border: token.colorBorderSecondary,
    },
})

/**
 * @param {Object} props
 * @param {string} [props.id] Rendered as `data-testid`.
 * @param {string} [props.state] One of `PILL_STATES`; anything else is drawn as
 *   `neutral` rather than uncoloured.
 * @param {import('react').ReactNode} [props.text] The label. Falls back to
 *   `children`, so both `text="3"` and a composed child work.
 * @param {import('react').ReactNode} [props.children]
 * @param {string} [props.title] Tooltip AND accessible name. Set it whenever the
 *   visible label is a bare number: "3" alone tells a screen-reader user nothing.
 * @param {string} [props.href] Wraps the pill in a link when given.
 * @param {boolean} [props.pulse] Marks an in-flight state. Honours
 *   `prefers-reduced-motion` - see the note above.
 * @param {PillColors} [props.colors] Overrides the token palette for callers
 *   with their own documented one.
 * @param {import('react').ReactNode} [props.icon] Optional glyph before the label.
 */
export default function StatePill({
                                      id,
                                      state,
                                      text,
                                      children,
                                      title,
                                      href,
                                      pulse = false,
                                      colors,
                                      icon,
                                  }) {

    const {token} = theme.useToken()

    const resolvedState = /** @type {PillState} */ (
        PILL_STATES.includes(/** @type {PillState} */ (state)) ? state : 'neutral'
    )
    const palette = colors ?? pillColorsFor(token)[resolvedState]

    const content = text ?? children

    const pill = (
        <span
            data-testid={id}
            data-state={resolvedState}
            title={title}
            className={pulse ? 'ot-pulse' : undefined}
            // `title` fires on neither keyboard focus nor touch, so it can never
            // be the only place the meaning lives - hence the label. It needs a
            // role to go with it: `aria-label` on a bare span (implicit
            // `role="generic"`) is not reliably exposed, which would leave the
            // sentence with nowhere to live at all.
            role={title ? 'img' : undefined}
            aria-label={title}
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: token.marginXXS,
                // Tight but not cramped: these sit inside table cells and on top
                // of 22px medals.
                padding: `0 ${token.paddingXXS}px`,
                minWidth: token.controlHeightSM / 1.5,
                height: token.controlHeightSM * 0.75,
                justifyContent: 'center',
                borderRadius: token.borderRadiusSM,
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.background,
                color: palette.color,
                fontFamily: token.fontFamily,
                fontSize: token.fontSizeSM,
                lineHeight: 1,
                fontVariantNumeric: 'tabular-nums',
                whiteSpace: 'nowrap',
                userSelect: 'none',
            }}
        >
            {icon}
            {content}
        </span>
    )

    // The link wraps the pill rather than sitting inside it, so the whole
    // coloured surface is the hit target.
    return href ? <Link href={href} style={{color: 'inherit'}}>{pill}</Link> : pill
}

// @ts-check

import {theme} from "antd"
import {getValidationRunStatusConfig} from "@components/validationRuns/ValidationRunStatusConfig"
import {useTheme} from "@components/providers/ThemeProvider"

/**
 * The colours that carry a validation run's state.
 *
 * THE HUES ARE NOT ANT DESIGN TOKENS, and that is deliberate.
 * `ValidationRunStatusConfig` is the single source of truth for how a validation
 * status is drawn, and it documents a verified contrast ratio for every pair it
 * defines. A second palette here would leave the product with two, and the
 * contrast baseline would stop being checkable. Tokens supply the neutral
 * surfaces, the radii and the type; the status supplies its own hue.
 *
 * Root statuses (the run's actual outcome) get the config's fill/glyph pair -
 * the combination whose contrast is documented. Derived statuses (triage applied
 * afterward) are drawn as a coloured outline on a neutral surface. That keeps
 * the two tiers tellable apart by more than hue, which is the same contract the
 * status marks honour with their disc-versus-squircle shapes.
 */

/**
 * The same triple `StatePill` paints itself with - referenced rather than
 * restated, so the two cannot drift.
 *
 * @typedef {import('@components/primitives/StatePill').PillColors} ValidationStateColors
 */

/**
 * @param {any} config From `getValidationRunStatusConfig`.
 * @param {'light'|'dark'} mode
 * @param {Record<string, any>} token
 * @returns {ValidationStateColors}
 */
export function validationStateColors(config, mode, token) {
    if (config.tier === 'root') {
        return {
            color: config.glyph,
            background: config.fill,
            border: config.fill,
        }
    }
    const hue = config.color[mode]
    return {
        color: hue,
        background: token.colorFillQuaternary,
        border: hue,
    }
}

/**
 * Resolves a status id straight to its colours, reading the theme side and the
 * token set for the caller. Never throws: an unknown or absent status resolves
 * through the config's own fallbacks.
 *
 * @param {string} [statusId]
 * @returns {ValidationStateColors}
 */
export function useValidationStateColors(statusId) {
    const {token} = theme.useToken()
    const {resolvedTheme} = useTheme()
    const mode = /** @type {'light'|'dark'} */ (resolvedTheme ?? 'light')
    return validationStateColors(getValidationRunStatusConfig(statusId), mode, token)
}

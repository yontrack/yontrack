// @ts-check

import {
    getValidationRunStatusConfig,
    SHAPE_TOKENS,
    SIZE_VARIANTS,
} from "@components/validationRuns/ValidationRunStatusConfig"

/**
 * Renders the mark for a validation run status.
 *
 * Every visual value - tier, shape, glyph, colour, size - comes from
 * `ValidationRunStatusConfig`. Nothing is hardcoded here and nothing belongs in
 * CSS. To change how a status looks, edit the config table, not this file.
 *
 * The accessibility contract this component implements is documented in full at
 * the top of `ValidationRunStatusConfig.js`. In short:
 *
 *   - The tier's SHAPE (filled disc vs outlined squircle) carries the meaning
 *     alongside colour, so the distinction survives greyscale and small sizes.
 *   - `aria-label` is always present, regardless of variant, hover or focus.
 *   - The glyph is `aria-hidden`, so it cannot compete with that label.
 *   - The `title` tooltip is only ever an extra convenience on the compact
 *     variant; the name always also exists as the accessible name, because
 *     `title` fires on neither keyboard focus nor touch.
 *
 * This component renders the MARK ONLY - it never renders a text label. The
 * 'full' variant is intended to be used next to a caller-supplied visible label
 * (see `SIZE_VARIANTS.full.showLabel`), which is how `ValidationRunStatus`
 * already does it.
 *
 * @param {Object} props
 * @param {{id: string, name?: string}} props.statusID Status to render. `name`
 *   is used as the accessible name; when absent the raw `id` is used, so the
 *   mark is never left unnamed.
 * @param {'full'|'compact'} [props.variant] Defaults to 'compact' (~22px), the
 *   size used in dense tables and rows.
 * @param {'light'|'dark'} [props.mode] Which side of the derived palette to
 *   use. Defaults to 'light'. The app has no dark theme yet; when one lands,
 *   wire this to the theme provider rather than to each call site.
 * @param {boolean} [props.tooltip] Force the native `title` tooltip on or off.
 *   Defaults to on for the compact variant only. Pass `false` when the caller
 *   already supplies its own tooltip (an antd Tooltip/Popover), to avoid
 *   stacking two tooltips on the same element.
 * @param {string} [props.className]
 */
export default function ValidationRunStatusIcon({
                                                    statusID,
                                                    variant = 'compact',
                                                    mode = 'light',
                                                    tooltip,
                                                    className,
                                                }) {

    const id = statusID?.id
    // Never leave the mark unnamed: fall back to the raw id.
    const statusName = statusID?.name || id || 'Unknown status'

    const config = getValidationRunStatusConfig(id)
    const shape = SHAPE_TOKENS[config.tier]
    const size = SIZE_VARIANTS[variant] ?? SIZE_VARIANTS.compact

    const isRoot = config.tier === 'root'

    // Root: reversed glyph on a solid disc. Derived: glyph matches the border.
    // Empty: no glyph at all, so no glyph colour either.
    const glyphColor = isRoot ? config.glyph : config.color[mode]

    // Tooltip is a convenience for the icon-only variant, never the sole
    // carrier of the name - `aria-label` below is always set either way.
    const showTooltip = tooltip ?? !size.showLabel

    /** @type {import('react').CSSProperties} */
    const style = {
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxSizing: 'border-box',
        // Fixed square box so discs stay circular and squircles stay regular,
        // and so neither is squashed by a flex parent.
        width: size.box,
        height: size.box,
        flex: `0 0 ${size.box}px`,
        borderRadius: shape.radius,
        backgroundColor: isRoot ? config.fill : 'transparent',
        border: isRoot
            ? undefined
            : `${shape.borderWidth}px ${shape.borderStyle} ${config.color[mode]}`,
        lineHeight: 0,
    }

    // react-icons declares IconType as returning ReactNode, which TypeScript
    // only accepts as a JSX component from TS 5.1 onward. The cast is a
    // toolchain workaround, not a claim about the value: these components are
    // built by react-icons' GenIcon and do return a JSX element.
    const Icon = /** @type {((props: import('react-icons').IconBaseProps) => import('react').ReactElement) | null} */ (config.Icon)

    return (
        <span
            role="img"
            aria-label={statusName}
            title={showTooltip ? statusName : undefined}
            className={className}
            data-status={id}
            data-tier={config.tier}
            style={style}
        >
            {
                // The empty mark has no glyph on purpose - the dashed, hollow
                // shape IS the mark. The wrapper still carries the accessible
                // name, so the absence stays announced.
                Icon && <Icon
                    size={size.glyph}
                    color={glyphColor}
                    // Bumped at compact size so fine detail (TbTool, TbBug) does
                    // not muddy - see the legibility note in the config.
                    strokeWidth={size.strokeWidth}
                    // Decorative: the name lives on the wrapper above. Note we do
                    // NOT pass react-icons' `title` prop, which would inject an
                    // SVG <title> and create a second, competing accessible name.
                    aria-hidden="true"
                    focusable="false"
                />
            }
        </span>
    )
}

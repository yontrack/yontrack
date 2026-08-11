// @ts-check

/**
 * Single source of truth for how validation run statuses are drawn.
 *
 * Every status's tier, glyph and colour lives in this file and NOWHERE ELSE.
 * Do not copy these hex values into CSS, into styled components, or into the
 * components that consume this table. If a colour needs to change, it changes
 * here and everything downstream follows.
 *
 * Glyphs come from Tabler, via `react-icons/tb` — already a dependency of this
 * app and already the icon convention used across it (see `react-icons/fa`
 * usages). There is deliberately no separate `@tabler/icons-react` dependency.
 *
 * ---------------------------------------------------------------------------
 * TWO TIERS
 * ---------------------------------------------------------------------------
 *
 * The tier is the *shape* of the container, and the shape is what makes the two
 * kinds of status tellable apart when colour is unavailable — greyscale, low
 * vision, colour blindness, or a 22px icon on a dense table row.
 *
 *   'root'    The run's actual outcome.
 *             SOLID FILLED DISC with a reversed (knocked-out) glyph.
 *
 *   'derived' Triage applied afterward by a human.
 *             LIGHT OUTLINED SQUIRCLE — 1.5px coloured border, transparent
 *             interior, glyph in the same colour as the border.
 *
 * ---------------------------------------------------------------------------
 * TIER DOES NOT MATCH THE BACKEND'S `root` FLAG — THIS IS DELIBERATE
 * ---------------------------------------------------------------------------
 *
 * `ValidationRunStatusID.kt` (ontrack-model/.../structure/ValidationRunStatusID.kt)
 * sets `isRoot = true` for INTERRUPTED and INVESTIGATING. This table classifies
 * both as 'derived', because visually they are triage applied after the fact and
 * should read as such.
 *
 * Consumers MUST read `tier` from this table and MUST NOT read `statusID.root`
 * to decide how to render. Please don't "fix" this to match the backend.
 *
 * ---------------------------------------------------------------------------
 * ACCESSIBILITY CONTRACT — the component author must honour all of these
 * ---------------------------------------------------------------------------
 *
 * 1. SHAPE + COLOUR CARRY THE MEANING TOGETHER. Never colour alone. A user who
 *    cannot distinguish #0F6E56 from #993C1D must still be able to tell a root
 *    outcome from derived triage, because one is a disc and one is a squircle.
 *    Do not introduce a variant whose only distinguishing feature is hue.
 *
 * 2. EVERY RENDERED MARK GETS `aria-label={statusName}`, ALWAYS PRESENT.
 *    Not on hover. Not only in the compact variant. Always. This is the
 *    accessible name of the mark and the only thing a screen reader announces.
 *
 * 3. PUT THE NAME ON THE WRAPPER, HIDE THE GLYPH.
 *    The wrapper element carries `role="img"` and `aria-label`; the Tabler SVG
 *    inside carries `aria-hidden="true"`.
 *
 *    Specifically: DO NOT use react-icons' `title` prop. It injects an SVG
 *    `<title>` element which becomes a *second* accessible name competing with
 *    the aria-label. If you want a tooltip, use the HTML `title` attribute on
 *    the wrapper (see 4) or an Ant Design Tooltip/Popover.
 *
 * 4. A TOOLTIP IS A CONVENIENCE, NEVER THE ONLY PLACE THE NAME EXISTS.
 *    A `title` tooltip is allowed as an extra affordance on the compact variant.
 *    It must never be the sole carrier of the status name: `title` does not fire
 *    on keyboard focus and does not fire on touch. The compact variant therefore
 *    needs BOTH the tooltip AND the aria-label from (2).
 *
 * 5. TWO SIZE VARIANTS, see `SIZE_VARIANTS` below.
 *    - 'full'    ~30px mark WITH a visible adjacent text label.
 *    - 'compact' ~22px mark, icon only, tooltip + aria-label.
 *
 * ---------------------------------------------------------------------------
 * CONTRAST BASELINE — verified, all pairs clear WCAG AA (4.5:1)
 * ---------------------------------------------------------------------------
 *
 * Recorded so that any future palette edit has something to re-check against.
 * If you change a hex value below, recompute these.
 *
 *   ROOT, glyph on its own fill:
 *     PASSED  #FFFFFF on #3B6D11   6.21:1
 *     FAILED  #FFFFFF on #A32D2D   7.07:1
 *     WARNING #412402 on #EF9F27   6.54:1
 *
 *   DERIVED, light colour on a white page:      6.20:1 – 6.96:1
 *   DERIVED, dark colour on #141414 (antd dark) 8.38:1 – 9.17:1
 *
 * ---------------------------------------------------------------------------
 * GLYPH LEGIBILITY AT COMPACT SIZE — flagged for the component author
 * ---------------------------------------------------------------------------
 *
 * Tabler artwork is drawn on a 24px grid at strokeWidth 2. Rendered at the
 * compact glyph size (~13px) that stroke effectively halves and fine detail
 * muddies. Three glyphs are at risk, worst first:
 *
 *   TbTool          (FIXED)     — the crossing wrench/screwdriver smudges into
 *                                 a blob at 13px. Worst of the eight.
 *   TbBug           (DEFECTIVE) — the thin legs drop out.
 *   TbAlertTriangle (WARNING)   — the internal bar/dot gets cramped inside the
 *                                 triangle outline.
 *
 * Mitigation: render the compact variant at `strokeWidth={2.25}` and the full
 * variant at `strokeWidth={2}` (see `SIZE_VARIANTS.*.strokeWidth`). Passing
 * strokeWidth as a prop does override the Tabler default — react-icons' IconBase
 * spreads caller props after `conf.attr`.
 *
 * If TbTool still reads poorly at 13px after the stroke bump, TbSettings or
 * TbWand are simpler silhouettes — but that is a design decision, so it is
 * flagged here rather than silently swapped.
 */

import {
    TbAlertTriangle,
    TbBug,
    TbCheck,
    TbMessage2,
    TbPlayerPause,
    TbQuestionMark,
    TbSearch,
    TbTool,
    TbX,
} from "react-icons/tb"

/**
 * The set of statuses this table knows how to draw.
 *
 * Typing the table as `Record<StatusKey, ...>` means adding an entry that is not
 * one of these, or omitting one of these, is an error under `// @ts-check`.
 *
 * @typedef {'PASSED'|'FAILED'|'WARNING'|'FIXED'|'EXPLAINED'|'INVESTIGATING'|'DEFECTIVE'|'INTERRUPTED'} StatusKey
 */

/**
 * 'root' and 'derived' are the two real status tiers. 'empty' is not a status
 * at all - it is the absence of one (no run yet), drawn as an empty slot so it
 * can never be mistaken for an outcome. See `EMPTY_STATUS_MARK`.
 *
 * @typedef {'root'|'derived'|'empty'} StatusTier
 */

/**
 * A Tabler glyph, as react-icons declares it.
 *
 * Note for consumers: react-icons types `IconType` as returning `ReactNode`,
 * which TypeScript only accepts as a JSX component type from TS 5.1 onward.
 * Rendering one under an older TypeScript needs a cast at the usage site - see
 * `ValidationRunStatusIcon.js`. The type is kept honest here rather than
 * loosened, so the cast stays visible where it is actually needed.
 *
 * @typedef {import('react-icons').IconType} GlyphComponent
 */

/**
 * A run's actual outcome: solid filled disc, reversed glyph.
 *
 * @typedef {Object} RootStatusConfig
 * @property {'root'} tier
 * @property {GlyphComponent} Icon Tabler glyph.
 * @property {string} fill Disc fill colour.
 * @property {string} glyph Reversed glyph colour, sits on `fill`.
 */

/**
 * Triage applied afterward: light outlined squircle, coloured glyph.
 *
 * @typedef {Object} DerivedStatusConfig
 * @property {'derived'} tier
 * @property {GlyphComponent} Icon Tabler glyph.
 * @property {{light: string, dark: string}} color Used for BOTH the border and
 *   the glyph — they are always the same colour in this tier.
 */

/**
 * The absence of a status: an empty slot, no glyph at all.
 *
 * @typedef {Object} EmptyStatusConfig
 * @property {'empty'} tier
 * @property {null} Icon Deliberately no glyph - see `EMPTY_STATUS_MARK`.
 * @property {{light: string, dark: string}} color
 */

/** @typedef {RootStatusConfig | DerivedStatusConfig | EmptyStatusConfig} StatusConfig */

/**
 * THE table. One entry per status; tier, glyph and colours all live here.
 *
 * @type {Record<StatusKey, StatusConfig>}
 */
export const VALIDATION_RUN_STATUS_CONFIG = {

    // --- ROOT: the run's actual outcome -------------------------------------

    PASSED: {
        tier: 'root',
        Icon: TbCheck,
        fill: '#3B6D11',
        glyph: '#FFFFFF',
    },
    FAILED: {
        tier: 'root',
        Icon: TbX,
        fill: '#A32D2D',
        glyph: '#FFFFFF',
    },
    WARNING: {
        tier: 'root',
        Icon: TbAlertTriangle,
        fill: '#EF9F27',
        // Dark brown rather than white: white on this amber is only ~2.2:1.
        glyph: '#412402',
    },

    // --- DERIVED: triage applied afterward -----------------------------------

    FIXED: {
        tier: 'derived',
        Icon: TbTool,
        color: {light: '#0F6E56', dark: '#5DCAA5'},
    },
    EXPLAINED: {
        tier: 'derived',
        Icon: TbMessage2,
        color: {light: '#185FA5', dark: '#85B7EB'},
    },
    INVESTIGATING: {
        tier: 'derived',
        Icon: TbSearch,
        color: {light: '#534AB7', dark: '#AFA9EC'},
    },
    DEFECTIVE: {
        tier: 'derived',
        Icon: TbBug,
        color: {light: '#993C1D', dark: '#F0997B'},
    },
    INTERRUPTED: {
        tier: 'derived',
        Icon: TbPlayerPause,
        color: {light: '#5F5E5A', dark: '#B4B2A9'},
    },
}

/**
 * Container geometry per tier. Exported so the rendering component reads the
 * shape from here rather than inventing its own radii.
 *
 * `radius` is a CSS border-radius value; `borderWidth` is in px.
 *
 * The three tiers are distinguishable by outline alone, with neither colour nor
 * glyph: filled disc / solid squircle / dashed empty squircle.
 *
 * @type {Record<StatusTier, {radius: string|number, borderWidth: number, borderStyle: string}>}
 */
export const SHAPE_TOKENS = {
    // Solid filled disc.
    root: {radius: '50%', borderWidth: 0, borderStyle: 'none'},
    // Squircle — rounded enough to be clearly not-a-circle at 22px.
    derived: {radius: 9, borderWidth: 1.5, borderStyle: 'solid'},
    // Same squircle, dashed and empty: reads as a slot to fill, not an outcome.
    empty: {radius: 9, borderWidth: 1.5, borderStyle: 'dashed'},
}

/**
 * The two planned size variants.
 *
 * `box`   outer container size in px.
 * `glyph` glyph size in px inside that container.
 * `strokeWidth` Tabler stroke width; bumped at compact size to keep detail from
 *   muddying — see the glyph legibility note in the header.
 * `showLabel` whether a visible text label sits next to the mark. When false the
 *   name still MUST be exposed via aria-label; a tooltip alone is not enough.
 *
 * @type {Record<'full'|'compact', {box: number, glyph: number, strokeWidth: number, showLabel: boolean}>}
 */
export const SIZE_VARIANTS = {
    full: {box: 30, glyph: 16, strokeWidth: 2, showLabel: true},
    compact: {box: 22, glyph: 13, strokeWidth: 2.25, showLabel: false},
}

/**
 * Status id meaning "there is no run here yet". Not a status the backend can
 * return on a run; `ValidationRunStatusNone` renders it for an empty cell.
 */
export const NONE_STATUS_ID = 'NONE'

/**
 * The absence of a status: an EMPTY dashed squircle with NO glyph.
 *
 * Deliberately glyphless. An earlier version drew a pause glyph here, which made
 * "no run yet" pixel-identical to a real INTERRUPTED status — an empty cell read
 * as an outcome. Whatever this mark becomes, it must never borrow the glyph of a
 * real status.
 *
 * The dashed outline also does the work of an affordance: in a validation grid
 * these cells are clickable to create a run, and an empty dashed slot reads as
 * "fillable" rather than as noise. Dashes carry that on their own, so this stays
 * legible in greyscale.
 *
 * @type {EmptyStatusConfig}
 */
export const EMPTY_STATUS_MARK = {
    tier: 'empty',
    Icon: null,
    // Same neutral grey as INTERRUPTED; the dashed, glyphless shape is what
    // separates them, so no new colour enters the palette.
    color: {light: '#5F5E5A', dark: '#B4B2A9'},
}

/**
 * Mark for a status id this table does not know — i.e. one the backend gained
 * after this file was last touched.
 *
 * A question mark, NOT a borrowed glyph: an unrecognised status must not
 * impersonate a real one. Rendered as 'derived' because an unknown status is
 * more likely triage than an outcome, and because understating it is the safer
 * error.
 *
 * Callers must still supply the accessible name — use the raw status id when no
 * display name is available.
 *
 * @type {DerivedStatusConfig}
 */
export const FALLBACK_STATUS_CONFIG = {
    tier: 'derived',
    Icon: TbQuestionMark,
    color: {light: '#5F5E5A', dark: '#B4B2A9'},
}

/**
 * Resolves a status id to its drawing config.
 *
 * Never throws and never returns undefined:
 *   - a known status  -> its entry in the table
 *   - NONE, or no id  -> `EMPTY_STATUS_MARK` (an empty slot, not a status)
 *   - anything else   -> `FALLBACK_STATUS_CONFIG` (an unknown status)
 *
 * So neither a missing run nor a status added on the backend can break a page.
 *
 * @param {string} [id] Status id, e.g. from `statusID.id`.
 * @returns {StatusConfig}
 */
export function getValidationRunStatusConfig(id) {
    if (!id || id === NONE_STATUS_ID) return EMPTY_STATUS_MARK
    return VALIDATION_RUN_STATUS_CONFIG[/** @type {StatusKey} */ (id)] ?? FALLBACK_STATUS_CONFIG
}

/**
 * Theme-aware defaults for the Recharts surfaces.
 *
 * Recharts does not follow antd's algorithm - its axes, grid and tooltip
 * default to colours picked for a white page. These bundles put the same
 * `data-theme` custom properties the rest of the application uses onto the SVG
 * presentation attributes and inline styles Recharts hands through, so the
 * charts switch with everything else and without a re-render.
 *
 * Spread them into the corresponding element, e.g.
 * `<CartesianGrid strokeDasharray="3 3" {...chartGridProps}/>`.
 */

/** For `<CartesianGrid/>`. */
export const chartGridProps = {
    stroke: 'var(--ot-chart-grid)',
}

/** For `<XAxis/>` and `<YAxis/>`. */
export const chartAxisProps = {
    stroke: 'var(--ot-chart-axis)',
    tick: {fill: 'var(--ot-chart-axis-text)'},
}

/** For `<Tooltip/>`, whose default is an opaque white card. */
export const chartTooltipProps = {
    contentStyle: {
        backgroundColor: 'var(--ot-chart-tooltip-bg)',
        border: '1px solid var(--ot-chart-grid)',
        borderRadius: 6,
        color: 'var(--ot-chart-text)',
    },
    labelStyle: {color: 'var(--ot-chart-text)'},
    itemStyle: {color: 'var(--ot-chart-text)'},
    // The default hover band is a near-black wash, invisible on a dark page.
    cursor: {fill: 'var(--ot-chart-cursor)'},
}

/** For `<Legend/>`, whose text otherwise inherits nothing. */
export const chartLegendProps = {
    wrapperStyle: {color: 'var(--ot-chart-text)'},
}

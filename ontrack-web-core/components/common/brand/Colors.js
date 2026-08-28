/**
 * The brand palette.
 *
 * `colors` holds the raw brand values. They are the identity and are the same
 * in both themes - the header keeps its purple whichever theme is active.
 *
 * `series` is what charts should use. A raw brand colour is not always legible
 * on both backgrounds: the brand purple all but disappears on antd's dark
 * surface. The dark variants are *derived* from the brand colours (same hue,
 * lifted lightness), not invented; the actual values live next to the other
 * theme tokens in `styles/globals.css`, keyed off `data-theme`, so a chart
 * follows the theme without re-rendering.
 */
export const brand = {
    colors: {
        lilac: "#D1B0F5",
        purple: "#3F3053",
        green: "#9FF58B",
        gray: "#E6E1E9",
        light_gray: "#F6F2F9",
    },
    series: {
        lilac: "var(--ot-chart-series-lilac)",
        purple: "var(--ot-chart-series-purple)",
        green: "var(--ot-chart-series-green)",
    },
}

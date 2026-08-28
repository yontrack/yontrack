import {MarkerType} from "reactflow";

/**
 * A single neutral grey rather than a `--ot-*` custom property, on purpose.
 *
 * ReactFlow builds the SVG marker's `id` by serializing the marker options,
 * colour included, and then references it as `url('#...')`. A `var(...)` value
 * would end up inside that fragment identifier, so the arrowhead cannot take a
 * custom property the way the edge line can. Keeping the line and its arrowhead
 * the same colour matters more than following the theme here, so both use this
 * value - a mid grey that clears 3:1 against the light page and against antd's
 * dark surface alike.
 */
const edgeColour = '#7d7d7d'

export const edgeStyle = {
    type: 'smoothstep',
    style: {
        stroke: edgeColour,
        strokeWidth: 2,
    },
    markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 20,
        height: 20,
        color: edgeColour,
    },
}

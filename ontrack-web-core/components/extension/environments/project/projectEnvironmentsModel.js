import {MarkerType} from "reactflow"
import {SCOPE_ALL} from "@components/extension/environments/matrix/environmentMatrixModel"

/**
 * The reading half of the project environments screen, kept apart from the drawing half.
 *
 * Which view is showing, which qualifier it is showing, and what a slot graph becomes once React
 * Flow has to draw it - all of it is arithmetic over the address bar and over what the server
 * answered, so it lives here and can be tested without mounting a graph.
 */

/** The two views of a project's environments: the dependency graph, and the home matrix pinned to it. */
export const VIEW_GRAPH = 'graph'
export const VIEW_MATRIX = 'matrix'

/** The qualifier every slot has unless somebody named one - `Slot.DEFAULT_QUALIFIER` on the server. */
export const DEFAULT_QUALIFIER = ''

/** How the default qualifier is named on screen, since "" reads as a missing option. */
export const DEFAULT_QUALIFIER_LABEL = 'Default'

/**
 * The view and the qualifier live in the URL, like the matrix's filter and the drawer's `?slot=`.
 *
 * Same argument as both of those: "look at petclinic's canary graph" is a link, not a description
 * of which controls to set, and the back button is then in charge of undoing a switch of view. The
 * screen holds no copy of either, so what it shows and what the address says cannot differ.
 *
 * @param {Object} query The Next router query
 * @return {{view: string, qualifier: string}}
 */
export const projectViewFromQuery = (query) => {
    // A parameter repeated in the URL arrives as an array. One view and one qualifier, so the first
    // wins - the same rule the slot drawer applies to `?slot=`.
    const single = (value) => Array.isArray(value) ? value[0] : value

    const view = single(query?.view)
    const qualifier = single(query?.qualifier)

    return {
        // An unknown view is not an error worth a screen: it falls back to the graph, which is what
        // the route means when it says nothing.
        view: view === VIEW_MATRIX ? VIEW_MATRIX : VIEW_GRAPH,
        qualifier: qualifier ?? DEFAULT_QUALIFIER,
    }
}

/**
 * The query parameters a view becomes - only what is not the default.
 *
 * A URL carrying `view=graph&qualifier=` when nothing has been chosen is noise, and it makes two
 * identical screens look like two different addresses.
 */
export const projectViewToQuery = ({view, qualifier}) => {
    const query = {}
    if (view && view !== VIEW_GRAPH) query.view = view
    if (qualifier) query.qualifier = qualifier
    return query
}

/**
 * The qualifiers a project has, as options for the selector.
 *
 * The default qualifier is always offered, whether or not a slot carries it: it is the view the
 * route opens on, and a selector which could not go back to it would be a trap. The rest are
 * alphabetical, after it - the default is the project's main line and belongs first whatever it
 * sorts as.
 *
 * @param {Array<string>} qualifiers Every qualifier seen on a slot of the project, duplicates and all
 * @return {Array<{value: string, label: string}>}
 */
export const qualifierOptions = (qualifiers) => {
    const named = Array.from(new Set((qualifiers ?? []).filter(it => !!it))).sort()
    return [
        {value: DEFAULT_QUALIFIER, label: DEFAULT_QUALIFIER_LABEL},
        ...named.map(qualifier => ({value: qualifier, label: qualifier})),
    ]
}

/**
 * Has this project more than one qualifier? What decides whether the selector is drawn at all.
 *
 * The spec's "a qualifier selector when the project has several": a project with only the default
 * qualifier - which is most of them - gets a control with one option and no choice to make, and the
 * screen is better off without it.
 */
export const hasSeveralQualifiers = (options) => (options ?? []).length > 1

/**
 * Every qualifier a project's slots carry, read from the answer to `gqlProjectQualifiers`.
 *
 * The question is asked of the environments rather than of the project because that is where the
 * schema keeps slots: `Environment.slots(projects:)`. What comes back is one list per environment,
 * so a qualifier used in three environments appears three times - `qualifierOptions` is what makes
 * it a set.
 */
export const qualifiersFromEnvironments = (environments) =>
    (environments ?? []).flatMap(environment => (environment.slots ?? []).map(slot => slot.qualifier))

/**
 * The graph's nodes: one per slot, and the slot itself is the whole of a node's data.
 *
 * `SlotCell` is a pure component - it draws what it is handed and asks the server for nothing - so
 * the node carries the cell's data and nothing more. What happens on a click is *not* in here: it
 * would be captured at the moment the layout ran and go stale, and the graph reads it from context
 * instead. See `ProjectSlotGraph`.
 */
export const slotGraphNodes = (slotGraph) =>
    (slotGraph?.slotNodes ?? []).map(slotNode => ({
        id: slotNode.slot.id,
        // ELK decides the real position; React Flow needs one before it has.
        position: {x: 0, y: 0},
        data: {slot: slotNode.slot},
        type: 'slotNode',
    }))

/**
 * The graph's edges: one per parent, pointing from the environment before to the one after.
 *
 * The direction is what the graph exists to show - a slot's parents are the slots it is admitted
 * *from*, which is the `environment` admission rule a matrix cannot draw - so the arrow head is on
 * the target and the layout runs left to right.
 */
export const slotGraphEdges = (slotGraph) =>
    (slotGraph?.slotNodes ?? []).flatMap(slotNode =>
        (slotNode.parents ?? []).map(parent => ({
            id: `${parent.id}-${slotNode.slot.id}`,
            source: parent.id,
            target: slotNode.slot.id,
            type: 'smoothstep',
            markerEnd: {
                type: MarkerType.ArrowClosed,
                width: 20,
                height: 20,
            },
        }))
    )

/**
 * The matrix filter the Matrix view pins: this project, and nothing else.
 *
 * `projects` is the exact-name pin rather than the toolbar's `project` fragment, and the scope is
 * All: a project reached from its own page is shown whether or not the reader ever starred it.
 */
export const projectMatrixFilter = (project) => ({
    projects: project?.name ? [project.name] : [],
    scope: SCOPE_ALL,
})

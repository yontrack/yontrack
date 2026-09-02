import {FaListUl, FaStream} from "react-icons/fa";
import BuildsContentView from "@components/branches/views/BuildsContentView";
import PipelineContentView from "@components/branches/views/pipeline/PipelineContentView";

/**
 * Registry of the branch content views: the interchangeable renderings which can fill the content
 * region of the branch view.
 *
 * Content views are peers on a single axis of choice — a content view never offers a sub-selector
 * for other content views. See `docs/adr/0001-branch-content-views.md`.
 *
 * Each entry carries:
 *
 * * `key` — stable identifier, used in the `?view=` parameter and in the user preferences
 * * `name` — label shown in the view selector
 * * `icon` — icon shown in the view selector
 * * `component` — the React component filling the content region, receiving only the `branch`
 *
 * This list is static on purpose: there is no extension point for contributing views yet. The entry
 * shape is nevertheless one a server-driven list could populate later.
 */
export const branchContentViews = [
    {
        key: 'builds',
        name: "Builds",
        icon: <FaListUl/>,
        component: BuildsContentView,
    },
    {
        key: 'pipeline',
        name: "Pipeline",
        icon: <FaStream/>,
        component: PipelineContentView,
    },
]

/**
 * Key of the content view used when no other choice applies. Existing users keep the builds view.
 */
export const defaultBranchContentViewKey = 'builds'

/**
 * Gets the content view registered under the given key, falling back to the default view when the
 * key names no known view (unknown `?view=` parameter, stale preference, nothing selected yet).
 *
 * @param key Key of the wanted content view
 * @param views List of content views to look into (defaults to the registry)
 */
export function getBranchContentView(key, views = branchContentViews) {
    return views.find(it => it.key === key) ??
        views.find(it => it.key === defaultBranchContentViewKey) ??
        views[0]
}

import {useCallback, useRef} from "react";
import {useRouter} from "next/router";
import {usePreferences} from "@components/providers/PreferencesProvider";
import {branchContentViews, getBranchContentView} from "@components/branches/views/branchContentViews";

/**
 * Name of the query parameter naming the selected branch content view.
 */
export const branchContentViewParam = 'view'

/**
 * Selection of the branch content view.
 *
 * The `?view=` parameter is authoritative and stays in the URL, so that any choice is linkable. This
 * deliberately differs from the neighbouring `buildFilter` parameter, which is consumed and cleared
 * on load because the filter it names is stored server-side.
 *
 * In the absence of a parameter, the user's own `selectedBranchViewKey` preference applies. It is one
 * global key per user, not one per project or per branch.
 *
 * @param views List of available content views (defaults to the registry)
 */
export default function useBranchContentViewSelection({views = branchContentViews} = {}) {

    const router = useRouter()
    const {selectedBranchViewKey, setPreferences} = usePreferences()

    const selectedViewKey = getBranchContentView(
        router.query?.[branchContentViewParam] ?? selectedBranchViewKey,
        views,
    )?.key

    // The branch view builds its selector into a `commands` array from inside an effect, which captures
    // this callback. `useRouter` hands out a fresh copy of the query on each render, so a captured
    // callback would write back the query and the preference of the render it was captured in — losing
    // a `buildFilter` set since, or misfiring the guard below. The callback is therefore given a stable
    // identity and reads what it needs at call time.
    const latest = useRef(null)
    latest.current = {views, router, selectedBranchViewKey, setPreferences}

    const selectBranchContentView = useCallback((viewKey) => {
        const {views, router, selectedBranchViewKey, setPreferences} = latest.current
        // Guards against a caller naming a view which is not registered
        if (!views.some(it => it.key === viewKey)) return
        // Remembers the choice for the next branch the user visits
        if (viewKey !== selectedBranchViewKey) {
            setPreferences({selectedBranchViewKey: viewKey})
        }
        // Makes the choice linkable
        router.replace(
            {
                pathname: router.pathname,
                query: {
                    ...router.query,
                    [branchContentViewParam]: viewKey,
                },
            },
            undefined,
            {shallow: true},
        )
    }, [])

    return {
        views,
        selectedViewKey,
        selectBranchContentView,
    }
}

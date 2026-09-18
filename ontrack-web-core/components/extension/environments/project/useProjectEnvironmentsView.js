import {useRouter} from "next/router"
import {
    projectViewFromQuery,
    projectViewToQuery,
} from "@components/extension/environments/project/projectEnvironmentsModel"

/**
 * Which view of a project's environments is showing, and for which qualifier - both from the URL.
 *
 * The same argument as the matrix's filter and the drawer's `?slot=`, and deliberately the same
 * shape: `?view=matrix&qualifier=canary` is what makes "look at petclinic's canary graph" a link
 * rather than a description of which controls to set, and it puts the back button in charge of
 * undoing a switch. A `useState` would give none of that, and the two would then have to be kept in
 * step.
 *
 * The navigation is `shallow` for the same reason the drawer's is: switching view or qualifier
 * changes what the screen asks the *server*, which the components' own `deps` handle, and re-running
 * the page's data fetching on top of that would only fetch the project twice.
 *
 * The hook is not ready until the router is - Next fills `router.query` on a second render for a
 * statically-optimised page, and reading the view before that would draw the graph for a tick and
 * then jump to the matrix somebody actually asked for.
 */
export const useProjectEnvironmentsView = () => {
    const router = useRouter()
    const ready = router?.isReady ?? true

    const {view, qualifier} = projectViewFromQuery(router?.query)

    /**
     * @param {Object} changes The fields to change - `view`, `qualifier`, or both
     */
    const apply = (changes) => {
        const next = {view, qualifier, ...changes}
        const query = {...router.query}
        // Both fields are rewritten, so one going back to its default leaves the address instead of
        // lingering in it.
        delete query.view
        delete query.qualifier
        router.push(
            {pathname: router.pathname, query: {...query, ...projectViewToQuery(next)}},
            undefined,
            {shallow: true},
        )
    }

    return {view, qualifier, apply, ready}
}

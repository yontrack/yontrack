import {useRouter} from "next/router"
import {useEffect, useState} from "react"
import {
    defaultMatrixFilter,
    filterFromQuery,
    filterToQuery,
    SCOPE_ALL,
} from "@components/extension/environments/matrix/environmentMatrixModel"
import {
    getLocalEnvironmentMatrixFilter,
    setLocalEnvironmentMatrixFilter,
} from "@components/storage/local"

/**
 * The matrix toolbar's state, which lives in the URL.
 *
 * **The URL is the filter.** `?scope=all&tags=production` is what makes a filtered matrix
 * bookmarkable and shareable - "look at production" is a link, not a description of which controls
 * to set - and it puts the back button in charge of undoing a filter change, which is what a reader
 * expects. A `useState` would give none of that, and the two would then have to be kept in step.
 * The same argument as the slot drawer's `?slot=`, and for the same reason the navigation is
 * `shallow`.
 *
 * The browser's last choice is remembered *beside* the URL rather than in it: it is where a visit
 * with no instruction starts, and it loses to any instruction there is.
 *
 * The hook is not ready until the router is: Next fills `router.query` on a second render for a
 * statically-optimised page, and reading the filter before that would show the defaults for a tick
 * and then jump to what the address actually says.
 */
export const useMatrixFilter = () => {
    const router = useRouter()
    const routerReady = router?.isReady ?? true

    // Read once, on the client: local storage does not exist while rendering on the server, and the
    // stored value must not change the first render's output either or React would see a mismatch.
    const [stored, setStored] = useState(null)
    const [storedRead, setStoredRead] = useState(false)
    useEffect(() => {
        try {
            setStored(getLocalEnvironmentMatrixFilter())
        } catch (ignored) {
            setStored(null)
        }
        setStoredRead(true)
    }, [])

    const filter = routerReady && storedRead
        ? filterFromQuery(router?.query, stored)
        : defaultMatrixFilter()

    /**
     * @param {Object} changes The filter fields to change
     * @param {boolean} replace Whether to replace the current history entry rather than add one.
     *   A change the *reader* made is a step they can take back; a correction the screen made on
     *   its own is not, and pushing one leaves a Back button that appears to do nothing - or, when
     *   the preference cannot be stored, one that undoes itself and traps the reader on the page.
     */
    const apply = (changes, {replace = false} = {}) => {
        const next = {...filter, ...changes}
        try {
            setLocalEnvironmentMatrixFilter(next)
        } catch (ignored) {
            // A browser refusing to store a preference is not a reason to refuse the filter
        }
        const query = {...router.query}
        // Everything the filter owns is rewritten, so a criterion going back to its default leaves
        // the URL instead of lingering there
        delete query.project
        delete query.scope
        delete query.label
        delete query.tags
        delete query.activity
        const navigate = replace ? router.replace : router.push
        navigate(
            {pathname: router.pathname, query: {...query, ...filterToQuery(next)}},
            undefined,
            {shallow: true},
        )
    }

    /**
     * What the "Show all" button of the empty state does, and what the screen does on its own for a
     * user who has never starred a project.
     */
    const showAll = () => apply({scope: SCOPE_ALL})

    return {filter, apply, showAll, ready: routerReady && storedRead}
}

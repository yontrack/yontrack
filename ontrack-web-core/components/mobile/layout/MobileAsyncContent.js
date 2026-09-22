"use client"

/**
 * The four states every mobile screen's content can be in, decided once.
 *
 * Reading a `useQuery` result correctly is fiddlier than it looks, and getting it
 * wrong is visible rather than subtle:
 *
 * - `useQuery` starts with `loading` false and only flips it inside its effect,
 *   so the very first render of a screen that trusted `loading` alone would show
 *   an empty state over data that is on its way.
 * - A screen that shows its skeleton whenever `loading` is true paints it *over*
 *   the answer it already has every time it refetches - which the favourite
 *   toggles make it do on every tap.
 *
 * So: a skeleton only until the first answer lands, the previous answer kept
 * (dimmed) while the next one is fetched, and an empty state only once a query
 * has actually finished with nothing.
 *
 * @param {{loading: boolean, error: any, finished: boolean}} state A `useQuery` result.
 * @param {string} errorMessage What to say above the error itself.
 * @param {boolean} isEmpty Whether the data that arrived is empty.
 * @param {React.ReactNode} empty What to show when it is.
 * @param {number} [rows] How tall the skeleton is.
 */

import {Alert, Skeleton} from "antd"

export default function MobileAsyncContent({state, errorMessage, isEmpty, empty, rows = 5, children}) {

    const {loading, error, finished} = state

    if (error) {
        return <Alert type="error" showIcon title={errorMessage} description={error}/>
    }

    const skeleton = <Skeleton active title={false} paragraph={{rows}}/>

    // Nothing has ever arrived: there is nothing to keep, so the skeleton is
    // what a user should see.
    if (!finished) return skeleton

    if (isEmpty) {
        // "There is nothing" is a statement the screen cannot make while it is
        // still asking - it would be contradicted a moment later.
        return loading ? skeleton : empty
    }

    return (
        <div className="ot-mobile-async" data-loading={loading ? 'true' : undefined}>
            {children}
        </div>
    )
}

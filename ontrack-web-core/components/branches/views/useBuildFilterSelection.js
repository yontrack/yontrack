import {useState} from "react";
import {useRouter} from "next/router";
import {getLocallySelectedBuildFilter, setLocallySelectedBuildFilter} from "@components/storage/local";

/**
 * The build filter a branch content view is showing its builds under.
 *
 * Shared by the content views rather than restated in each: which builds a branch shows is a
 * property of the branch the user is reading, not of the way they chose to read it, so switching
 * view must not silently drop the filter they had set.
 *
 * The `?buildFilter=` parameter is CONSUMED AND CLEARED on load - deliberately unlike the `?view=`
 * and `?build=` parameters beside it, which stay. Those two name a choice with nowhere else to live;
 * a build filter is stored server-side and locally, so leaving its whole JSON body in the address
 * bar would only make the URL unreadable and the state ambiguous.
 *
 * @param branch Branch being displayed
 */
export default function useBuildFilterSelection({branch}) {

    const router = useRouter()

    let initialBuildFilter = undefined
    const {buildFilter} = router.query
    if (buildFilter) {
        try {
            initialBuildFilter = JSON.parse(buildFilter)
            // Clears the permalink, leaving the other parameters (the content view, the selected
            // build) alone
            const {buildFilter: _, ...query} = router.query
            router.replace({pathname: router.pathname, query}, undefined, {shallow: true})
        } catch (ignored) {
        }
    } else {
        initialBuildFilter = getLocallySelectedBuildFilter(branch.id)
    }

    const [selectedBuildFilter, setSelectedBuildFilter] = useState(initialBuildFilter)

    const onSelectedBuildFilter = (resource) => {
        setLocallySelectedBuildFilter(branch.id, resource)
        setSelectedBuildFilter(resource)
    }

    const onPermalinkBuildFilter = (resource) => {
        if (resource) {
            router.replace({
                pathname: router.pathname,
                query: {
                    ...router.query,
                    buildFilter: JSON.stringify(resource),
                },
            }, undefined, {shallow: true})
        }
    }

    return {
        selectedBuildFilter,
        onSelectedBuildFilter,
        onPermalinkBuildFilter,
    }
}

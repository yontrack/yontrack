import {useEffect, useState} from "react";
import {useRouter} from "next/router";
import {getLocallySelectedBoundaryFilter, setLocallySelectedBoundaryFilter} from "@components/storage/local";
import {parseBoundaryFilter, sanitizeBoundaryFilter} from "@components/extension/findings/findingsModel";

/**
 * The build filter a finding page reads the end of the exposure periods against: a personal view
 * setting, per project and not per finding, kept apart from the filters of the Classic view, so that
 * choosing one there does not change this page, nor the reverse.
 *
 * The `?boundaryFilter=` permalink is consumed and cleared on load, as `?buildFilter=` is by
 * `useBuildFilterSelection`.
 *
 * @param project Project of the finding
 */
export default function useBoundaryFilterSelection({project}) {

    const router = useRouter()

    const [boundaryFilter, setBoundaryFilter] = useState(() =>
        parseBoundaryFilter(router.query.boundaryFilter) ??
        sanitizeBoundaryFilter(getLocallySelectedBoundaryFilter(project.id))
    )

    // Once, on load: a permalink asked for afterwards stays in the address bar, to be copied
    useEffect(() => {
        if (router.query.boundaryFilter) {
            const {boundaryFilter: _, ...query} = router.query
            router.replace({pathname: router.pathname, query}, undefined, {shallow: true})
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const onBoundaryFilter = (filter) => {
        setLocallySelectedBoundaryFilter(project.id, filter)
        setBoundaryFilter(filter)
    }

    const onBoundaryFilterPermalink = () => {
        if (boundaryFilter) {
            router.replace({
                pathname: router.pathname,
                query: {
                    ...router.query,
                    boundaryFilter: JSON.stringify(boundaryFilter),
                },
            }, undefined, {shallow: true})
        }
    }

    return {
        boundaryFilter,
        onBoundaryFilter,
        onBoundaryFilterPermalink,
    }
}

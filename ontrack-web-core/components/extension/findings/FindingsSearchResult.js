import {useEffect} from "react";
import {Alert, Empty, Skeleton} from "antd";
import FindingsRankTable from "@components/extension/findings/FindingsRankTable";
import {rankedFindingRows, searchedFindingsCaption} from "@components/extension/findings/findingFanOutModel";

/**
 * What a search among the external IDs of the findings found, one row per external ID, ranked: a
 * click on a row opens the fan-out of its finding. When only one finding is found, its fan-out is
 * opened right away.
 *
 * @param findings Findings found
 * @param finished Whether the search is over
 * @param error Error of the search
 * @param text Text searched among the external IDs
 * @param size Number of findings asked for
 * @param emptyDescription What to say when nothing is found
 * @param testId Prefix of the test IDs of the list
 * @param onSelect Called with the external ID of the finding whose row is clicked
 * @param onOnlyOne Called with the external ID of the only finding found
 */
export default function FindingsSearchResult({
                                                 findings,
                                                 finished,
                                                 error,
                                                 text,
                                                 size,
                                                 emptyDescription,
                                                 testId,
                                                 onSelect,
                                                 onOnlyOne,
                                             }) {

    const rows = finished && !error ? rankedFindingRows(findings) : []
    const onlyOne = rows.length === 1 ? rows[0].externalId : null

    // Opening the fan-out is a change of the state of the page, kept by its parent: not while
    // rendering. Once per finding found, whatever the identity of the callback, recreated by each
    // render of the parent.
    useEffect(() => {
        if (onlyOne) {
            onOnlyOne(onlyOne)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [onlyOne])

    if (!finished || onlyOne) {
        return <Skeleton active/>
    } else if (error) {
        return <Alert type="error" showIcon title={error}/>
    } else if (rows.length === 0) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription}/>
    }

    return <FindingsRankTable
        rows={rows}
        caption={searchedFindingsCaption(rows.length, size, text)}
        testId={testId}
        onSelect={onSelect}
    />
}

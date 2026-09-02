import {useEffect, useRef} from "react";
import {Button, Empty, Popover, Space, Spin, theme, Typography} from "antd";
import {FaSearch} from "react-icons/fa";
import BuildTimelineCard from "@components/branches/views/pipeline/BuildTimelineCard";

/**
 * The builds of the branch, most recent first, as a horizontally scrolling strip of cards.
 *
 * The strip scrolls rather than paginating, and "load more" sits at its end with exactly the
 * semantics - and exactly the query - the legacy table's has: one more page of the same filter,
 * appended. A user who switches views mid-scroll gets the same builds either way.
 *
 * @param builds Loaded builds, most recent first
 * @param loading Whether a page is in flight
 * @param pageInfo Page info of the last loaded page
 * @param onLoadMore Loads the next page
 * @param promotionLevels The branch's levels, lowest first
 * @param selectedFilter The active validation stamp filter, if any
 * @param selectedBuildId The build being inspected
 * @param onSelect Called with a build id
 * @param rangeSelection Range selection state
 */
export default function BuildTimeline({
                                          builds,
                                          loading,
                                          pageInfo,
                                          onLoadMore,
                                          promotionLevels,
                                          selectedFilter,
                                          selectedBuildId,
                                          onSelect,
                                          rangeSelection,
                                      }) {

    const {token} = theme.useToken()

    // Cards are addressed by build id so that a selection made elsewhere - a stage card naming its
    // latest build - can bring its card into view instead of leaving the user to hunt for it in a
    // strip which may be several screens wide.
    const cardRefs = useRef(new Map())
    useEffect(() => {
        const card = selectedBuildId ? cardRefs.current.get(String(selectedBuildId)) : null
        // `nearest` on the block axis: the strip scrolls horizontally, and scrolling the *page* to
        // bring a card into view would yank the user away from whatever they were reading.
        card?.scrollIntoView({behavior: 'smooth', block: 'nearest', inline: 'center'})
    }, [selectedBuildId])

    if (!loading && (!builds || builds.length === 0)) {
        return (
            <Empty
                data-testid="pipeline-no-builds"
                description="No build to show on this branch."
            />
        )
    }

    return (
        <Space direction="vertical" size={token.marginXS} className="ot-line">
            <div
                data-testid="build-timeline"
                style={{
                    display: 'flex',
                    alignItems: 'stretch',
                    gap: token.marginSM,
                    overflowX: 'auto',
                    paddingBottom: token.paddingXS,
                }}
            >
                {
                    (builds ?? []).map(build =>
                        <BuildTimelineCard
                            key={build.id}
                            ref={(element) => {
                                if (element) {
                                    cardRefs.current.set(String(build.id), element)
                                } else {
                                    cardRefs.current.delete(String(build.id))
                                }
                            }}
                            build={build}
                            promotionLevels={promotionLevels}
                            selectedFilter={selectedFilter}
                            selected={String(build.id) === String(selectedBuildId)}
                            onSelect={onSelect}
                            rangeSelection={rangeSelection}
                        />
                    )
                }
                {/* At the end of the strip, where the builds run out - not above it */}
                <div style={{flex: '0 0 auto', display: 'flex', alignItems: 'center'}}>
                    <Popover
                        content={
                            pageInfo?.nextPage ?
                                "There are more builds to be loaded" :
                                "There are no more builds to be loaded"
                        }
                    >
                        <Button
                            data-testid="timeline-load-more"
                            onClick={onLoadMore}
                            disabled={!pageInfo?.nextPage || loading}
                        >
                            <Space>
                                {loading ? <Spin size="small"/> : <FaSearch/>}
                                <Typography.Text>Load more...</Typography.Text>
                            </Space>
                        </Button>
                    </Popover>
                </div>
            </div>
        </Space>
    )
}

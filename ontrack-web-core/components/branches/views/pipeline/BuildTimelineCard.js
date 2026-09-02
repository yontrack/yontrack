import {forwardRef} from "react";
import {Space, theme, Typography} from "antd";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import RangeSelector from "@components/common/RangeSelector";
import TimestampText from "@components/common/TimestampText";
import ValidationStrip from "@components/branches/views/pipeline/ValidationStrip";
import {
    buildVersion,
    filterValidations,
    topPromotionRuns,
    validationStrip,
} from "@components/branches/views/pipeline/pipelineFacts";

/**
 * One build in the timeline.
 *
 * THE CARD IS A REAL BUTTON. Selecting a build is an action, so it is reachable by Tab, operable by
 * Enter and Space, and announced as pressed - none of which a `div` with an `onClick` gives, and all
 * of which this view depends on because selection is the only way to read the inspector.
 *
 * The RANGE SELECTOR is a sibling of that button rather than a child of it: a control nested inside
 * a button is invalid, and the two affordances mean different things - one picks the build to
 * inspect, the other marks a change log boundary.
 *
 * @param build The build to draw
 * @param promotionLevels The branch's levels, lowest first, used to rank the medals
 * @param selectedFilter The active validation stamp filter, if any
 * @param selected Whether this build is the one being inspected
 * @param onSelect Called with the build id
 * @param rangeSelection Range selection state, shared with the change log button
 * @param width Fixed card width
 */
const BuildTimelineCard = forwardRef(function BuildTimelineCard({
                                                                   build,
                                                                   promotionLevels,
                                                                   selectedFilter,
                                                                   selected,
                                                                   onSelect,
                                                                   rangeSelection,
                                                                   width = 220,
                                                               }, ref) {

    const {token} = theme.useToken()

    const version = buildVersion(build)
    const {shown, overflow} = topPromotionRuns(build.promotionRuns, promotionLevels)
    const {bars, passed, total} = validationStrip(filterValidations(build.validations, selectedFilter))

    return (
        <div
            ref={ref}
            style={{position: 'relative', flex: `0 0 ${width}px`, width}}
        >
            <button
                type="button"
                data-testid={`timeline-build-${build.id}`}
                aria-pressed={selected}
                onClick={() => onSelect?.(build.id)}
                style={{
                    width: '100%',
                    textAlign: 'left',
                    cursor: 'pointer',
                    padding: token.paddingSM,
                    // The right padding leaves the range selector its corner
                    paddingRight: token.paddingSM + token.controlHeightSM,
                    borderRadius: token.borderRadiusLG,
                    border: `1px solid ${selected ? token.colorPrimary : token.colorBorderSecondary}`,
                    // Selection is carried by the border AND the surface, so it survives a theme
                    // where the primary hue is close to the border one.
                    backgroundColor: selected ? token.colorPrimaryBg : token.colorBgContainer,
                    fontFamily: token.fontFamily,
                }}
            >
                <Space direction="vertical" size={token.marginXXS} style={{width: '100%'}}>
                    <Typography.Text strong ellipsis={true}>
                        {version ?? build.name}
                    </Typography.Text>
                    {
                        // Only when it says something the line above does not. For a project with no
                        // release property the two are the same string.
                        version &&
                        <Typography.Text type="secondary" ellipsis={true} style={{fontSize: token.fontSizeSM}}>
                            {build.name}
                        </Typography.Text>
                    }
                    <Space size={token.marginXXS}>
                        {
                            shown.map(run =>
                                <PromotionLevelImage
                                    key={run.id}
                                    promotionLevel={run.promotionLevel}
                                    size={20}
                                    tooltipText={run.promotionLevel?.name}
                                />
                            )
                        }
                        {
                            overflow > 0 &&
                            <Typography.Text
                                type="secondary"
                                style={{fontSize: token.fontSizeSM}}
                                title={`${overflow} more promotion(s)`}
                            >
                                +{overflow}
                            </Typography.Text>
                        }
                    </Space>
                    <Typography.Text type="secondary" style={{fontSize: token.fontSizeSM}}>
                        <TimestampText value={build.creation?.time}/>
                    </Typography.Text>
                    <ValidationStrip bars={bars} passed={passed} total={total}/>
                </Space>
            </button>
            <span
                style={{position: 'absolute', top: token.paddingSM, right: token.paddingXS}}
                // The two affordances are distinct: marking a change log boundary must not also
                // change which build the inspector describes.
                onClick={(event) => event.stopPropagation()}
            >
                <RangeSelector
                    id={build.id}
                    title="Select this build as a boundary for a change log."
                    rangeSelection={rangeSelection}
                />
            </span>
        </div>
    )
})

export default BuildTimelineCard

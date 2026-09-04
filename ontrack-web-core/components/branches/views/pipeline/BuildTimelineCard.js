import {forwardRef} from "react";
import {Space, theme, Typography} from "antd";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import RangeSelector from "@components/common/RangeSelector";
import TimestampText from "@components/common/TimestampText";
import ValidationStrip from "@components/branches/views/pipeline/ValidationStrip";
import Decoration from "@components/framework/decorations/Decoration";
import {
    buildVersion,
    filterValidations,
    topPromotionRuns,
    validationStrip,
    visibleDecorations,
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
 * THE DECORATIONS ROW is a sibling for the same reason - a decoration is a link, and an interactive
 * descendant of a button is invalid: one click would both navigate and re-select. That is why the
 * card chrome (border, surface, padding) lives on the container and the button is transparent: the
 * button covers the part of the card which means "inspect this build", and the row below it sits
 * inside the same card without being part of that action.
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
    // `version` so that a decoration saying only what the title says is not drawn twice
    const {shown: decorations, overflow: decorationOverflow} =
        visibleDecorations(build.decorations, {version})

    return (
        <div
            ref={ref}
            style={{
                position: 'relative',
                flex: `0 0 ${width}px`,
                width,
                // The timeline stretches every card to the tallest one. The container is what paints
                // the card, so its children have to grow into that height - otherwise the surface
                // below a short card's content looks like card and does not select the build.
                display: 'flex',
                flexDirection: 'column',
                borderRadius: token.borderRadiusLG,
                border: `1px solid ${selected ? token.colorPrimary : token.colorBorderSecondary}`,
                // Selection is carried by the border AND the surface, so it survives a theme
                // where the primary hue is close to the border one.
                backgroundColor: selected ? token.colorPrimaryBg : token.colorBgContainer,
            }}
        >
            <button
                type="button"
                data-testid={`timeline-build-${build.id}`}
                aria-pressed={selected}
                onClick={() => onSelect?.(build.id)}
                style={{
                    display: 'block',
                    // Takes the slack the stretched container has, so all of it stays clickable
                    flex: '1 1 auto',
                    width: '100%',
                    textAlign: 'left',
                    cursor: 'pointer',
                    // The border and the surface are the container's; the button keeps its own
                    // padding, so that everything it covers stays clickable.
                    padding: token.paddingSM,
                    // The right padding leaves the range selector its corner
                    paddingRight: token.paddingSM + token.controlHeightSM,
                    border: 'none',
                    background: 'none',
                    color: 'inherit',
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
                                // The wrapper carries the test id: `PromotionLevelImage` is a thin
                                // shared wrapper which must not grow props of its own, and a medal
                                // needs to be addressable to check that it goes when its run does.
                                <span
                                    key={run.id}
                                    data-testid={`timeline-medal-${build.id}-${run.promotionLevel?.id}`}
                                >
                                    <PromotionLevelImage
                                        promotionLevel={run.promotionLevel}
                                        size={20}
                                        tooltipText={run.promotionLevel?.name}
                                    />
                                </span>
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
            {
                // Guards the case where the backend sends no decorations at all - an older instance,
                // or one running no decorating extension. It is NOT the empty-looking-row case:
                // `BuildLinkDecorationExtension` contributes a decoration for EVERY build and then
                // renders nothing when the build has no links, and core cannot tell the two apart
                // without reading an extension's data. Such a row costs its own padding and no more,
                // equally on every card, which is why that is left alone rather than guessed at.
                decorations.length > 0 &&
                <Space
                    data-testid={`timeline-build-decorations-${build.id}`}
                    size={token.marginXXS}
                    wrap
                    // Aligned on the button's own padding, the two making one card
                    style={{
                        paddingLeft: token.paddingSM,
                        paddingRight: token.paddingSM,
                        paddingBottom: token.paddingSM,
                    }}
                >
                    {
                        // Every decoration, unfiltered. Picking out the environments one would mean
                        // core code naming an extension, which is the coupling this seam avoids.
                        decorations.map(decoration =>
                            <Decoration key={decoration.decorationType} decoration={decoration}/>
                        )
                    }
                    {
                        decorationOverflow > 0 &&
                        <Typography.Text
                            type="secondary"
                            style={{fontSize: token.fontSizeSM}}
                            title={`${decorationOverflow} more decoration(s)`}
                        >
                            +{decorationOverflow}
                        </Typography.Text>
                    }
                </Space>
            }
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

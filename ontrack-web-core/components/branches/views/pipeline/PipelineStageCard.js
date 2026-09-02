import {Popover, Space, theme, Typography} from "antd";
import Link from "next/link";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import {promotionLevelUri} from "@components/common/Links";
import TimestampText from "@components/common/TimestampText";
import {buildKnownName} from "@components/common/Titles";

/**
 * One stage of the promotion pipeline: a promotion level, how many builds reached it, and the
 * latest build which did.
 *
 * A LEVEL NOBODY HAS REACHED STILL RENDERS, dimmed. The point of the band is the *shape* of the
 * pipeline - what a release has to go through - and a band which only shows the achieved part
 * cannot say that a branch has never once reached GOLD.
 *
 * There is no gold/silver/bronze gradient and no tier: promotion levels in Yontrack carry an
 * ordinal position and nothing else. The uploaded image wins, `GeneratedIcon` is the fallback.
 *
 * @param promotionLevel The level this stage stands for
 * @param promotedBuildCount How many distinct builds reached it
 * @param latestRun The most recent promotion run at this level, if any
 * @param onSelectBuild Called with the id of the latest build when it is picked
 * @param width Fixed card width, so a row of stages scans as a row
 */
export default function PipelineStageCard({
                                              promotionLevel,
                                              promotedBuildCount = 0,
                                              latestRun,
                                              onSelectBuild,
                                              width = 220,
                                          }) {

    const {token} = theme.useToken()

    const reached = promotedBuildCount > 0
    const latestBuild = latestRun?.build

    return (
        <div
            data-testid={`pipeline-stage-${promotionLevel.id}`}
            data-reached={reached}
            style={{
                flex: `0 0 ${width}px`,
                width,
                padding: token.paddingSM,
                borderRadius: token.borderRadiusLG,
                border: `1px solid ${token.colorBorderSecondary}`,
                backgroundColor: token.colorBgContainer,
                // Dimmed rather than absent: the stage is part of the pipeline's shape even when no
                // build has ever reached it.
                opacity: reached ? 1 : 0.45,
            }}
        >
            <Space direction="vertical" size={token.marginXXS} style={{width: '100%'}}>
                <Space size={token.marginXS}>
                    <PromotionLevelImage promotionLevel={promotionLevel} size={26}/>
                    <Popover
                        title={promotionLevel.name}
                        content={<AnnotatedDescription entity={promotionLevel}/>}
                    >
                        <Link href={promotionLevelUri(promotionLevel)}>
                            <Typography.Text strong ellipsis={true}>{promotionLevel.name}</Typography.Text>
                        </Link>
                    </Popover>
                </Space>
                <Typography.Text type="secondary" style={{fontSize: token.fontSizeSM}}>
                    {promotedBuildCount === 1 ? "1 build" : `${promotedBuildCount} builds`}
                </Typography.Text>
                {
                    latestBuild ?
                        <Typography.Text
                            data-testid={`pipeline-stage-${promotionLevel.id}-build`}
                            className="ot-action"
                            ellipsis={true}
                            // Selects the build in the timeline rather than leaving the branch: the
                            // whole point of the view is inspecting a build without navigating away.
                            onClick={() => onSelectBuild?.(latestBuild.id)}
                            title={`Select ${buildKnownName(latestBuild)} in the timeline`}
                        >
                            {buildKnownName(latestBuild)}
                        </Typography.Text> :
                        <Typography.Text type="secondary" italic style={{fontSize: token.fontSizeSM}}>
                            Never reached
                        </Typography.Text>
                }
                {
                    latestRun?.creation?.time &&
                    <Typography.Text type="secondary" style={{fontSize: token.fontSizeSM}}>
                        <TimestampText value={latestRun.creation.time}/>
                    </Typography.Text>
                }
            </Space>
        </div>
    )
}

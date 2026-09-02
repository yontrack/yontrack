import {Empty, Space, theme, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import PromotionLevel from "@components/promotionLevels/PromotionLevel";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import TimestampText from "@components/common/TimestampText";
import EntityNotificationsBadge from "@components/extension/notifications/EntityNotificationsBadge";
import BuildPromoteAction from "@components/builds/BuildPromoteAction";
import PromotionRunFieldValues from "@components/promotionRuns/PromotionRunFieldValues";
import {isAuthorized} from "@components/common/authorizations";
import {promotionRunUri} from "@components/common/Links";
import {nextPromotionLevel, topPromotionRuns} from "@components/branches/views/pipeline/pipelineFacts";

/**
 * The promotions of the inspected build.
 *
 * A NEW COMPOSITION, not the build page's `BuildContentPromotions` embedded. The two hosts optimise
 * for different reading: this panel is read while scanning a branch, that widget is read once you
 * have committed to one build. What they must not diverge on - how a promotion looks - is the shared
 * primitives underneath, not this arrangement. See `docs/adr/0003`.
 *
 * @param build The inspected build, with its promotion runs and its branch's promotion levels
 * @param onChange Called when a promotion is granted, so the caller can reload
 */
export default function BuildInspectorPromotions({build, onChange}) {

    const {token} = theme.useToken()

    const promotionLevels = build?.branch?.promotionLevels ?? []
    // Every run, ordered by the branch's own level order; `max` is the whole list because a panel,
    // unlike a timeline card, has the room.
    const {shown: runs} = topPromotionRuns(build?.promotionRuns, promotionLevels, Number.MAX_SAFE_INTEGER)

    // The lowest level the build has not reached is where a promote affordance belongs. It is hidden
    // outright when the user cannot promote - an affordance which only ever produces a permission
    // error is worse than no affordance.
    const next = nextPromotionLevel(promotionLevels, build?.promotionRuns)
    const canPromote = isAuthorized(build, 'build', 'promote')

    return (
        <PageSection id="inspector-promotions" title="Promotions" padding={true}>
            <Space direction="vertical" size={token.marginXS} className="ot-line">
                {
                    runs.length === 0 &&
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="This build has not been promoted."
                    />
                }
                {
                    runs.map(run =>
                        <Space
                            key={run.id}
                            size={token.marginXS}
                            wrap
                            data-testid={`inspector-promotion-${run.promotionLevel?.id}`}
                        >
                            {/* The shared composition, so a promotion reads the same here as on the
                                build page and in the builds table */}
                            <PromotionLevel
                                promotionLevel={run.promotionLevel}
                                size={24}
                                displayText={true}
                            />
                            <Typography.Text type="secondary">
                                <TimestampText value={run.creation?.time}/>
                                {run.creation?.user && ` by ${run.creation.user}`}
                            </Typography.Text>
                            <AnnotatedDescription entity={run}/>
                            {
                                run.fieldValues?.length > 0 &&
                                <PromotionRunFieldValues
                                    fields={run.promotionLevel?.fields}
                                    fieldValues={run.fieldValues}
                                />
                            }
                            {/* Counts notification RECORDS across every channel, not workflows */}
                            <EntityNotificationsBadge
                                entityType="PROMOTION_RUN"
                                entityId={run.id}
                                href={promotionRunUri(run)}
                            />
                        </Space>
                    )
                }
                {
                    next && canPromote &&
                    <BuildPromoteAction
                        build={build}
                        promotionLevel={next}
                        presentation="button"
                        onPromotion={onChange}
                    />
                }
            </Space>
        </PageSection>
    )
}

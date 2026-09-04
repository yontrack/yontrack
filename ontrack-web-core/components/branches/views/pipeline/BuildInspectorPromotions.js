import Link from "next/link";
import {Empty, Space, theme, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import PromotionLevel from "@components/promotionLevels/PromotionLevel";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import TimestampText from "@components/common/TimestampText";
import EntityNotificationsBadge from "@components/extension/notifications/EntityNotificationsBadge";
import BuildPromoteAction from "@components/builds/BuildPromoteAction";
import PromotionRunFieldValues from "@components/promotionRuns/PromotionRunFieldValues";
import PromotionRunDeleteAction from "@components/promotionRuns/PromotionRunDeleteAction";
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

    // The lowest level the build has not reached is the dialog's DEFAULT, not the affordance's
    // identity: the dialog's level field is editable, so a button naming this level would describe a
    // restriction that has never existed. The affordance is hidden outright when the user cannot
    // promote - one which only ever produces a permission error is worse than none.
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
                        <div
                            key={run.id}
                            className="ot-row-hover"
                            // Keyed by RUN, not by level. The panel renders one row per run, and a
                            // build promoted twice to one level has two of them - so a level-keyed
                            // id would name two nodes, and "the run I deleted is gone" would be
                            // indistinguishable from "one of the two is still there". The level
                            // rides along as an attribute for callers who know a level and not a
                            // run id.
                            data-testid={`inspector-promotion-run-${run.id}`}
                            data-promotion-level={run.promotionLevel?.id}
                            // The actions sit immediately after the run they act on, not at the far
                            // edge of the panel: this panel is as wide as half the page, and
                            // `space-between` put a delete icon a screen-width away from the row it
                            // belonged to. Ragged right, because being next to the run matters more
                            // than the icons lining up with each other.
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: token.marginXS,
                            }}
                        >
                            <Space size={token.marginXS} wrap>
                                {/* The shared composition, so a promotion reads the same here as on
                                    the build page and in the builds table */}
                                <PromotionLevel
                                    promotionLevel={run.promotionLevel}
                                    size={24}
                                    displayText={true}
                                />
                                <Typography.Text type="secondary">
                                    {/* The way to the run, hung on the timestamp the row already
                                        renders. Unconditional, where the notifications badge below
                                        links only when the run happens to have records - which made
                                        navigation read as a notifications feature. */}
                                    <Link
                                        href={promotionRunUri(run)}
                                        data-testid={`inspector-promotion-run-link-${run.id}`}
                                        title="Goes to this promotion run."
                                    >
                                        <TimestampText value={run.creation?.time}/>
                                    </Link>
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
                            {/* Quiet until the row is hovered or something inside it takes focus, so
                                a list of promotions reads as a list rather than as a control panel.
                                Both gates are absence, not opacity: an action the user may not take
                                is not rendered at all. */}
                            <Space className="ot-row-actions" size={token.marginXS}>
                                {
                                    canPromote &&
                                    <BuildPromoteAction
                                        build={build}
                                        promotionLevel={run.promotionLevel}
                                        tooltip={`Promotes the build again to ${run.promotionLevel?.name}.`}
                                        onPromotion={onChange}
                                    />
                                }
                                {
                                    // Per RUN, not per build: the query already carries each run's
                                    // own authorizations, and a build promoted twice has two runs
                                    // which may not be equally deletable.
                                    isAuthorized(run, 'promotion_run', 'delete') &&
                                    <PromotionRunDeleteAction
                                        promotionRun={run}
                                        onDeletion={onChange}
                                    />
                                }
                            </Space>
                        </div>
                    )
                }
                {
                    // No `next` condition: once every level is reached there is no next one, and
                    // promoting again is still something the user can do.
                    canPromote &&
                    <BuildPromoteAction
                        build={build}
                        defaultPromotionLevel={next}
                        presentation="button"
                        onPromotion={onChange}
                    />
                }
            </Space>
        </PageSection>
    )
}

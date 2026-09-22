import {useState} from "react"
import Link from "next/link"
import {Alert, Button, Skeleton, Space, Steps, Typography} from "antd"
import {FaBan, FaCheck, FaPlay} from "react-icons/fa"
import {callGraphQL, useQuery} from "@components/services/GraphQL"
import {isAuthorized} from "@components/common/authorizations"
import {slotPipelineUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import TimestampText from "@components/common/TimestampText"
import WhatsBlocking from "@components/extension/environments/shared/WhatsBlocking"
import Freshness, {useFreshness} from "@components/extension/environments/shared/Freshness"
import {
    buildLabel,
    compactAge,
    isInFlight,
    topPromotionRun,
} from "@components/extension/environments/shared/slotCellModel"
import {
    gqlSlotCancel,
    gqlSlotDrawer,
    gqlSlotDrawerDeployment,
    gqlSlotDrawerFinish,
    gqlSlotDrawerRun,
} from "@components/extension/environments/shared/environmentsSharedGraphQL"

/**
 * **Now / In flight / Next / Recent** - the body of the slot drawer, and the header block of the
 * slot page.
 *
 * It was the drawer's own content until #1793; the slot page needs the same four sections, and the
 * redesign says "the same component" rather than "the same shape". Two renderings of one slot that
 * can disagree about what is deployed in it are exactly what this redesign set out to remove, so
 * the sections live here and the drawer is now a `Drawer` wrapped around them.
 *
 * Its five sections are in the order somebody actually asks them:
 *
 * 1. **Now** - what is deployed. The question the matrix was opened to answer, in full.
 * 2. **In flight** - what is trying to replace it, how far it got, what is blocking it, and the one
 *    action that would move it. Absent when nothing is in flight, rather than drawn empty.
 * 3. **Next** - up to three eligible builds newer than the one here, each with Deploy.
 * 4. **Recent** - the last five deployments, so "was this normal?" has an answer. The slot page
 *    turns this off: its Deployments tab is the full history, and five rows of it above the tab
 *    holding all of them would be the old page's stacked sections coming back.
 *
 * It polls every 30 seconds and says when it last heard: a slot left on screen while a deployment
 * runs is the exact case where a silently stale screen misleads.
 *
 * @param {string} slotId The slot to show.
 * @param {function} onDeploy Called with `(slot, build)` when the user asks to deploy; the caller
 *   owns the deploy dialog. Neither the drawer nor the page lets this component own it, because a
 *   screen holding several of these wants one dialog rather than one per rendering.
 * @param {boolean} recent Whether to draw the "Recent" section.
 * @param {function} onChange Called after an action here changed the deployment. The component
 *   refreshes itself either way; this is for a page which shows the *same* deployment somewhere
 *   else - the slot page's Deployments tab, whose rows would otherwise still say Candidate after
 *   the header block finished it.
 * @param {string} testId Prefix of every test id below. It defaults to the drawer's, so the tests
 *   written against the drawer in #1797 keep addressing the same elements.
 */
export default function SlotSummary({slotId, onDeploy, onChange, recent = true, testId = 'slot-drawer'}) {

    const {refreshCount, refreshedAt, refresh} = useFreshness()

    const [acting, setActing] = useState(false)
    const [error, setError] = useState(null)

    const query = useQuery(
        gqlSlotDrawer,
        {
            variables: {slotId},
            deps: [slotId, refreshCount],
            condition: !!slotId,
        }
    )

    /*
     * The in-flight deployment is a *second* query rather than a part of this one, because
     * `slotWorkflowInstanceForPipeline` and the admission rule checks all need the deployment's id,
     * which is only known once the slot has answered. Now, Next and Recent are drawn as soon as the
     * slot lands and the In flight section fills a moment later, which is the right order: a reader
     * who came to see what is deployed does not wait on the checks.
     */
    return <SlotSummaryBody
        slotId={slotId}
        query={query}
        refreshedAt={refreshedAt}
        refresh={refresh}
        onDeploy={onDeploy}
        onChange={onChange}
        acting={acting}
        setActing={setActing}
        error={error}
        setError={setError}
        recent={recent}
        testId={testId}
    />
}

function SlotSummaryBody({
                             slotId,
                             query,
                             refreshedAt,
                             refresh,
                             onDeploy,
                             onChange,
                             acting,
                             setActing,
                             error,
                             setError,
                             recent,
                             testId,
                         }) {

    const slot = query.data?.slotById

    // The slot's most recent deployment, whatever became of it, is the head of `pipelines`.
    const current = slot?.pipelines?.pageItems?.[0]
    const inFlight = isInFlight(current) ? current : null

    const detail = useQuery(
        gqlSlotDrawerDeployment,
        {
            variables: {pipelineId: inFlight?.id ?? ''},
            deps: [inFlight?.id, refreshedAt],
            condition: !!inFlight?.id,
            dataFn: data => data.slotPipelineById,
        }
    )

    const deployment = detail.data

    const canAct = isAuthorized(slot ?? {}, 'pipeline', 'create')

    const act = async (mutation, userNode, statusNode, variables = {}) => {
        setActing(true)
        setError(null)
        try {
            const data = await callGraphQL({query: mutation, variables: {id: inFlight.id, ...variables}})
            const payload = data?.[userNode]
            const errors = payload?.errors
            if (errors && errors.length > 0) {
                setError(errors[0].message)
            } else if (statusNode && payload?.[statusNode]?.ok === false) {
                setError(payload[statusNode].message)
            } else {
                refresh()
                onChange?.()
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setActing(false)
        }
    }

    const start = () => act(
        gqlSlotDrawerRun,
        'startSlotPipelineDeployment',
        'deploymentStatus',
    )

    const finish = () => act(
        gqlSlotDrawerFinish,
        'finishSlotPipelineDeployment',
        'finishStatus',
    )

    const cancel = () => act(
        gqlSlotCancel,
        'cancelSlotPipeline',
        null,
        {reason: "Cancelled from the slot drawer."},
    )

    if (query.error) {
        return <Alert type="error" showIcon title="Could not load the slot." description={query.error}
                      data-testid={`${testId}-error`}/>
    }

    if (!query.finished) return <Skeleton active paragraph={{rows: 8}}/>

    if (!slot) {
        return <Alert type="warning" showIcon title="No such slot." data-testid={`${testId}-missing`}/>
    }

    const deployed = slot.lastDeployedPipeline
    const deployedBuild = deployed?.build

    return (
        <Space orientation="vertical" size={16} className="ot-line" data-testid={`${testId}-${slotId}`}>

            <Freshness refreshedAt={refreshedAt} refresh={refresh} testId={`${testId}-freshness`}/>

            {error && <Alert type="error" showIcon title={error} data-testid={`${testId}-action-error`}/>}

            {/* 1 - Now */}
            <section data-testid={`${testId}-now`}>
                <Typography.Text type="secondary">NOW</Typography.Text>
                {
                    deployedBuild ?
                        <div>
                            <Space size={8} wrap>
                                <Typography.Text strong>{buildLabel(deployedBuild)}</Typography.Text>
                                {
                                    deployedBuild.branch &&
                                    <Typography.Text type="secondary">({deployedBuild.branch.name})</Typography.Text>
                                }
                                {
                                    topPromotionRun(deployedBuild) &&
                                    <PromotionLevelImage
                                        promotionLevel={topPromotionRun(deployedBuild).promotionLevel}
                                        size={16}
                                    />
                                }
                            </Space>
                            <div>
                                <Typography.Text type="secondary">
                                    {'Deployed '}
                                    <TimestampText value={deployed.end ?? deployed.start} relative={true}/>
                                    {deployed.lastChange?.user ? ` by ${deployed.lastChange.user}` : ''}
                                </Typography.Text>
                            </div>
                        </div> :
                        <div>
                            <Typography.Text type="secondary" data-testid={`${testId}-never-deployed`}>
                                Never deployed
                            </Typography.Text>
                        </div>
                }
            </section>

            {/* 2 - In flight */}
            {
                inFlight &&
                <section data-testid={`${testId}-in-flight`}>
                    <Typography.Text type="secondary">IN FLIGHT</Typography.Text>
                    <div>
                        <Space size={8} wrap>
                            <Link href={slotPipelineUri(inFlight.id)}>#{inFlight.number}</Link>
                            <Typography.Text strong>{buildLabel(inFlight.build)}</Typography.Text>
                            {
                                topPromotionRun(inFlight.build) &&
                                <PromotionLevelImage
                                    promotionLevel={topPromotionRun(inFlight.build).promotionLevel}
                                    size={16}
                                />
                            }
                        </Space>
                    </div>
                    <Steps
                        size="small"
                        current={inFlight.status === 'CANDIDATE' ? 0 : 1}
                        items={[
                            {title: 'Candidate'},
                            {title: 'Running'},
                            {title: 'Deployed'},
                        ]}
                        style={{marginTop: 8, marginBottom: 8}}
                    />
                    {
                        detail.finished && deployment &&
                        <WhatsBlocking
                            deployment={deployment}
                            onChange={refresh}
                            testId={`${testId}-whats-blocking`}
                        />
                    }
                    {
                        canAct &&
                        <Space style={{marginTop: 8}}>
                            {
                                inFlight.status === 'CANDIDATE' &&
                                <Button
                                    type="primary"
                                    icon={<FaPlay/>}
                                    loading={acting}
                                    disabled={!deployment?.runAction?.ok}
                                    data-testid={`${testId}-start`}
                                    onClick={start}
                                >
                                    Start the deployment
                                </Button>
                            }
                            {
                                inFlight.status === 'RUNNING' &&
                                <Button
                                    type="primary"
                                    icon={<FaCheck/>}
                                    loading={acting}
                                    disabled={!deployment?.finishAction?.ok}
                                    data-testid={`${testId}-finish`}
                                    onClick={finish}
                                >
                                    Finish the deployment
                                </Button>
                            }
                            <Button
                                danger
                                type="text"
                                icon={<FaBan/>}
                                loading={acting}
                                data-testid={`${testId}-cancel`}
                                onClick={cancel}
                            >
                                Cancel
                            </Button>
                        </Space>
                    }
                </section>
            }

            {/* 3 - Next */}
            <section data-testid={`${testId}-next`}>
                <Typography.Text type="secondary">NEXT</Typography.Text>
                {
                    (slot.nextBuilds ?? []).length === 0 ?
                        <div>
                            <Typography.Text type="secondary" data-testid={`${testId}-next-none`}>
                                {
                                    deployedBuild || inFlight ?
                                        `No eligible build newer than ${buildLabel((inFlight ?? deployed).build)}.` :
                                        'No eligible build.'
                                }
                            </Typography.Text>
                        </div> :
                        slot.nextBuilds.map(build => (
                            <div key={build.id} data-testid={`${testId}-next-${build.id}`}>
                                <Space size={8} wrap>
                                    <Typography.Text>{buildLabel(build)}</Typography.Text>
                                    {
                                        topPromotionRun(build) &&
                                        <PromotionLevelImage
                                            promotionLevel={topPromotionRun(build).promotionLevel}
                                            size={16}
                                        />
                                    }
                                    {
                                        canAct &&
                                        <Button
                                            size="small"
                                            icon={<FaPlay/>}
                                            data-testid={`${testId}-deploy-${build.id}`}
                                            onClick={() => onDeploy?.(slot, build)}
                                        >
                                            Deploy
                                        </Button>
                                    }
                                </Space>
                            </div>
                        ))
                }
            </section>

            {/* 4 - Recent */}
            {
                recent &&
                <section data-testid={`${testId}-recent`}>
                    <Typography.Text type="secondary">RECENT</Typography.Text>
                    {
                        (slot.pipelines?.pageItems ?? []).length === 0 ?
                            <div>
                                <Typography.Text type="secondary">No deployment yet.</Typography.Text>
                            </div> :
                            slot.pipelines.pageItems.map(pipeline => (
                                <div key={pipeline.id} data-testid={`${testId}-recent-${pipeline.number}`}>
                                    <Space size={8} wrap>
                                        <Link href={slotPipelineUri(pipeline.id)}>#{pipeline.number}</Link>
                                        <Typography.Text>{buildLabel(pipeline.build)}</Typography.Text>
                                        <Typography.Text type="secondary">{pipeline.status}</Typography.Text>
                                        <Typography.Text type="secondary">
                                            {compactAge(pipeline.end ?? pipeline.start)}
                                        </Typography.Text>
                                    </Space>
                                </div>
                            ))
                    }
                </section>
            }

        </Space>
    )
}

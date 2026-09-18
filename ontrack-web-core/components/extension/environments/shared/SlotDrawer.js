import {useState} from "react"
import Link from "next/link"
import {Alert, Button, Drawer, Skeleton, Space, Steps, Typography} from "antd"
import {FaBan, FaCheck, FaPlay} from "react-icons/fa"
import {callGraphQL, useQuery} from "@components/services/GraphQL"
import {isAuthorized} from "@components/common/authorizations"
import EnvironmentIcon from "@components/extension/environments/EnvironmentIcon"
import {slotPipelineUri, slotUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import TimestampText from "@components/common/TimestampText"
import WhatsBlocking from "@components/extension/environments/shared/WhatsBlocking"
import Freshness, {useFreshness} from "@components/extension/environments/shared/Freshness"
import {
    buildLabel,
    compactAge,
    isInFlight,
    slotDisplayName,
    topPromotionRun,
} from "@components/extension/environments/shared/slotCellModel"
import {
    gqlSlotDrawer,
    gqlSlotCancel,
    gqlSlotDrawerDeployment,
    gqlSlotDrawerFinish,
    gqlSlotDrawerRun,
    gqlSlotDrawerTitle,
} from "@components/extension/environments/shared/environmentsSharedGraphQL"

/**
 * The shared slot quick view.
 *
 * Every screen of the redesign that shows a slot can open this: the matrix, the project slot graph,
 * the build journey strip, the widgets and the delivery map's checkpoints. The top of the slot page
 * is this same component. That is the whole point of it - one answer to "what is going on in this
 * slot?", written once, rather than the four different summaries the feature grew.
 *
 * Its five sections are in the order somebody actually asks them:
 *
 * 1. **Header** - which slot this is, and the way out to the slot page.
 * 2. **Now** - what is deployed. The question the matrix was opened to answer, in full.
 * 3. **In flight** - what is trying to replace it, how far it got, what is blocking it, and the one
 *    action that would move it. Absent when nothing is in flight, rather than drawn empty.
 * 4. **Next** - up to three eligible builds newer than the one here, each with Deploy.
 * 5. **Recent** - the last five deployments, so "was this normal?" has an answer.
 *
 * It polls every 30 seconds and says when it last heard: a drawer left open while a deployment runs
 * is the exact case where a silently stale screen misleads.
 *
 * @param {string} slotId The slot to show - the drawer is addressed by `?slot=<id>`.
 * @param {boolean} open Whether it is up.
 * @param {function} onClose Close it.
 * @param {function} onDeploy Called with a slot when the user asks to deploy into it; the caller
 *   opens the deploy dialog. The drawer does not own the dialog, because a page holding both the
 *   matrix and the drawer wants one dialog, not one per open drawer.
 */
export default function SlotDrawer({slotId, open, onClose, onDeploy}) {
    return (
        <Drawer
            open={open}
            onClose={onClose}
            width={520}
            title={<SlotDrawerTitle slotId={slotId} open={open}/>}
            data-testid="slot-drawer"
            // Every opening asks the server again, and a closed drawer costs nothing.
            destroyOnClose
        >
            {
                /*
                 * `key` so that switching slots while the drawer is open remounts the content.
                 * `useQuery` never puts `finished` back to false on a deps change, so without this
                 * the drawer would keep drawing the previous slot's Now / In flight / Next / Recent
                 * under the new slot's title until the new answer landed.
                 */
                open && slotId &&
                <SlotDrawerContent key={slotId} slotId={slotId} onClose={onClose} onDeploy={onDeploy}/>
            }
        </Drawer>
    )
}

/**
 * The title is its own tiny query so the drawer has a name before the rest of it lands - opening
 * onto a blank header and having it fill in a moment later reads as a bug.
 */
function SlotDrawerTitle({slotId, open}) {
    const query = useQuery(
        gqlSlotDrawerTitle,
        {
            variables: {slotId},
            deps: [slotId],
            condition: !!slotId && open,
            dataFn: data => data.slotById,
        }
    )

    const slot = query.data
    if (!slot) return 'Slot'

    return (
        <Space size={8}>
            <EnvironmentIcon environmentId={slot.environment.id} size={16}/>
            <Typography.Text strong data-testid="slot-drawer-title">{slotDisplayName(slot)}</Typography.Text>
            <Link href={slotUri(slot)} data-testid="slot-drawer-open-slot">Open slot</Link>
        </Space>
    )
}

function SlotDrawerContent({slotId, onDeploy}) {

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
     * which is only known once the slot has answered. The drawer draws Now, Next and Recent as soon
     * as the slot lands and fills the In flight section a moment later, which is the right order: a
     * reader who opened the drawer to see what is deployed does not wait on the checks.
     */
    return <SlotDrawerBody
        slotId={slotId}
        query={query}
        refreshedAt={refreshedAt}
        refresh={refresh}
        onDeploy={onDeploy}
        acting={acting}
        setActing={setActing}
        error={error}
        setError={setError}
    />
}

function SlotDrawerBody({slotId, query, refreshedAt, refresh, onDeploy, acting, setActing, error, setError}) {

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
        return <Alert type="error" showIcon message="Could not load the slot." description={query.error}
                      data-testid="slot-drawer-error"/>
    }

    if (!query.finished) return <Skeleton active paragraph={{rows: 8}}/>

    if (!slot) {
        return <Alert type="warning" showIcon message="No such slot." data-testid="slot-drawer-missing"/>
    }

    const deployed = slot.lastDeployedPipeline
    const deployedBuild = deployed?.build

    return (
        <Space direction="vertical" size={16} className="ot-line" data-testid={`slot-drawer-${slotId}`}>

            <Freshness refreshedAt={refreshedAt} refresh={refresh} testId="slot-drawer-freshness"/>

            {error && <Alert type="error" showIcon message={error} data-testid="slot-drawer-action-error"/>}

            {/* 2 - Now */}
            <section data-testid="slot-drawer-now">
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
                            <Typography.Text type="secondary" data-testid="slot-drawer-never-deployed">
                                Never deployed
                            </Typography.Text>
                        </div>
                }
            </section>

            {/* 3 - In flight */}
            {
                inFlight &&
                <section data-testid="slot-drawer-in-flight">
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
                        <WhatsBlocking deployment={deployment} onChange={refresh}/>
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
                                    data-testid="slot-drawer-start"
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
                                    data-testid="slot-drawer-finish"
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
                                data-testid="slot-drawer-cancel"
                                onClick={cancel}
                            >
                                Cancel
                            </Button>
                        </Space>
                    }
                </section>
            }

            {/* 4 - Next */}
            <section data-testid="slot-drawer-next">
                <Typography.Text type="secondary">NEXT</Typography.Text>
                {
                    (slot.nextBuilds ?? []).length === 0 ?
                        <div>
                            <Typography.Text type="secondary" data-testid="slot-drawer-next-none">
                                {
                                    deployedBuild || inFlight ?
                                        `No eligible build newer than ${(inFlight ?? deployed).build.name}.` :
                                        'No eligible build.'
                                }
                            </Typography.Text>
                        </div> :
                        slot.nextBuilds.map(build => (
                            <div key={build.id} data-testid={`slot-drawer-next-${build.id}`}>
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
                                            data-testid={`slot-drawer-deploy-${build.id}`}
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

            {/* 5 - Recent */}
            <section data-testid="slot-drawer-recent">
                <Typography.Text type="secondary">RECENT</Typography.Text>
                {
                    (slot.pipelines?.pageItems ?? []).length === 0 ?
                        <div>
                            <Typography.Text type="secondary">No deployment yet.</Typography.Text>
                        </div> :
                        slot.pipelines.pageItems.map(pipeline => (
                            <div key={pipeline.id} data-testid={`slot-drawer-recent-${pipeline.number}`}>
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

        </Space>
    )
}

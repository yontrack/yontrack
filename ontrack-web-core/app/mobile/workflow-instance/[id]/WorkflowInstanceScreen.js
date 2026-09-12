"use client"

/**
 * One workflow run, on a phone: what it did, and where it got stuck.
 *
 * One screen for both kinds of run a phone can reach - the one a promotion set
 * off, listed on the build screen, and the one a slot workflow ran for a
 * deployment, listed on the deployment screen. They are the same
 * `WorkflowInstance` behind the same `workflowInstance(id:)` root query, so
 * keying on that id is what makes one screen enough. Everything slot-specific -
 * the trigger, the override, the reason a check gives - belongs to the
 * *deployment* rather than to the run, and stays on the deployment screen.
 *
 * **A vertical list, not a graph.** The desktop draws the DAG with React Flow
 * and elkjs in a fixed 600px box; a pan-and-zoom canvas on a phone is worse than
 * no canvas, and the shape of the graph is not what someone checking a blocked
 * deployment needs. The nodes are ordered by `workflowNodeDepths` - the
 * desktop's own pure, cycle-safe depth function - and then by declaration order
 * inside a depth, flattened into one list. A node is therefore always below
 * every one of its parents. No second ordering rule is invented for the phone.
 *
 * **`output` is left out.** It is arbitrary executor-specific JSON which the
 * desktop renders only through `Dynamic` component lookups, and `Dynamic` is the
 * one thing documented as unusable under `/mobile`. **`error` is not**: it is a
 * string, and it is shown inline on *every* failed node rather than only the
 * first - there is no side panel on a phone to go and find the others in.
 *
 * **This screen polls, and it is the only mobile screen that does.** Every other
 * refresh in this UI is a counter bumped after a write, because the build and
 * deployment screens are decision surfaces and a list reshuffling under a thumb
 * is worse than a stale one. A `RUNNING` workflow is precisely what someone
 * opens on a phone, though, and a frozen page says nothing about whether it is
 * moving - so this one asks again while `finished` is false, through the
 * desktop's own narrow second query, and stops the moment it flips.
 *
 * **Read-only, entirely.** No stop, no override. Overriding a blocking workflow
 * needs `SlotUpdate` *and* `SlotPipelineOverride`, a pair the role this UI is
 * built around does not hold; stopping one is destructive on a small screen with
 * none of #1736's "CI died and someone must act" counterweight.
 */

import {useEffect, useState} from "react"
import {gql} from "graphql-request"
import {Alert, Typography} from "antd"
import {useQuery} from "@components/services/GraphQL"
import MobileScreen from "@components/mobile/layout/MobileScreen"
import MobileSectionList from "@components/mobile/layout/MobileSectionList"
import MobileAsyncContent from "@components/mobile/layout/MobileAsyncContent"
import TimestampText from "@components/common/TimestampText"
import DurationMs from "@components/common/DurationMs"
import WorkflowInstanceStatus from "@components/extension/workflows/WorkflowInstanceStatus"
import WorkflowInstanceNodeStatus from "@components/extension/workflows/WorkflowInstanceNodeStatus"
import workflowNodeDepths from "@components/extension/workflows/workflowNodeDepths"

/**
 * How often a running workflow is asked about, in milliseconds.
 *
 * Slower than the desktop's own auto-refresh: a phone is on a mobile network and
 * on a battery, and a node moving two seconds later than it might have costs the
 * reader nothing.
 */
export const POLL_INTERVAL_MS = 5000

/**
 * What set this run off, in words.
 *
 * Deriving an actual *link* back to the origin was considered and rejected: a
 * slot instance carries `triggerData.data.pipelineId`, while a promotion one
 * carries only an opaque `notification-record` id whose resolution needs
 * `NotificationRecordingAccess`. Two branches so a user can avoid a back gesture
 * they already have is not worth it, so the origin is named rather than linked.
 */
const TRIGGER_NAMES = {
    'slot-pipeline': 'Run for a deployment',
    'notification-record': 'Run by a notification',
    'user': 'Run by hand',
}

export default function MobileWorkflowInstanceScreen({id}) {

    const query = useQuery(
        gql`
            query MobileWorkflowInstance($id: String!) {
                workflowInstance(id: $id) {
                    id
                    status
                    finished
                    startTime
                    endTime
                    durationMs
                    # What set it off, named rather than linked - see TRIGGER_NAMES.
                    triggerData {
                        id
                    }
                    workflow {
                        name
                        # The definition, which is what carries the order: the
                        # executions know only their own id.
                        nodes {
                            id
                            description
                            executorId
                            parents {
                                id
                            }
                        }
                    }
                    nodesExecutions {
                        id
                        status
                        error
                        durationMs
                    }
                }
            }
        `,
        {
            variables: {id},
            deps: [id],
            condition: !!id,
        }
    )

    const instance = query.data?.workflowInstance

    /** Bumped by the timer below, which is the whole of the polling. */
    const [tick, setTick] = useState(0)

    /*
     * The desktop's own narrow second query, reused rather than re-running the
     * full one: the workflow's definition cannot change under a running
     * instance, so asking for it again every few seconds would be paying for the
     * half that never moves.
     */
    const progressQuery = useQuery(
        gql`
            query MobileWorkflowInstanceProgress($id: String!) {
                workflowInstance(id: $id) {
                    status
                    finished
                    endTime
                    durationMs
                    nodesExecutions {
                        id
                        status
                        error
                        durationMs
                    }
                }
            }
        `,
        {
            variables: {id},
            deps: [id, tick],
            // Only while there is something to watch, and never before the first
            // answer has said whether there is.
            condition: !!id && tick > 0,
        }
    )

    const progress = progressQuery.data?.workflowInstance

    /*
     * The latest answer about the half that moves, whichever query brought it.
     * Derived while rendering rather than copied into state: it is a function of
     * the two answers and of nothing else.
     */
    const latest = progress ?? instance
    const finished = latest?.finished
    const nodesExecutions = latest?.nodesExecutions ?? []

    useEffect(() => {
        // `false` and not falsy: `undefined` is "no answer yet", and starting a
        // timer then would poll a run which may already be over.
        if (!id || finished !== false) return
        const timer = setInterval(() => setTick(count => count + 1), POLL_INTERVAL_MS)
        return () => clearInterval(timer)
    }, [id, finished])

    /*
     * The nodes in one column, depth by depth. `workflowNodeDepths` answers with
     * one array per depth, in declaration order inside each - flattening it is
     * the whole of the mobile ordering.
     */
    const nodes = workflowNodeDepths(instance?.workflow?.nodes ?? [], nodesExecutions).flat()

    const triggerName = instance?.triggerData?.id
        ? (TRIGGER_NAMES[instance.triggerData.id] ?? `Run by ${instance.triggerData.id}`)
        : null

    return (
        <MobileScreen
            title={instance?.workflow?.name || "Workflow"}
            subtitle={
                triggerName &&
                <span data-testid="mobile-workflow-instance-origin">{triggerName}</span>
            }
        >
            <MobileAsyncContent
                state={query}
                errorMessage="Could not load the workflow run."
                /*
                 * A null instance is a not-found, not an empty. `MobileAsyncContent`
                 * would otherwise draw an `Empty`, which reads as "this workflow
                 * has no content" rather than "no such workflow" - and following a
                 * stale shared link is exactly how someone gets here.
                 *
                 * `workflowInstance(id:)` also answers `null` for a run the user
                 * may not read (#1739), deliberately: an error would tell them a
                 * run they cannot see exists.
                 */
                isEmpty={query.finished && !instance}
                empty={
                    <Alert
                        type="warning"
                        showIcon
                        message="This workflow run could not be found."
                        data-testid="mobile-workflow-instance-missing"
                    />
                }
                rows={6}
            >
                {
                    instance &&
                    <div className="ot-mobile-stack">
                        <Typography.Text data-testid="mobile-workflow-instance-status">
                            <span className="ot-mobile-inline">
                                <WorkflowInstanceStatus
                                    id="mobile-workflow-instance-status-label"
                                    status={latest?.status}
                                />
                                <Typography.Text type="secondary" className="ot-mobile-caption">
                                    <TimestampText value={instance.startTime} prefix="started" relative/>
                                    {
                                        // Only once it is over: a duration on a
                                        // running workflow is the age of a
                                        // snapshot, not how long it took.
                                        finished && latest?.durationMs !== undefined &&
                                        <>
                                            {' — '}
                                            <DurationMs ms={latest.durationMs}/>
                                        </>
                                    }
                                </Typography.Text>
                            </span>
                        </Typography.Text>

                        <MobileSectionList
                            title="Nodes"
                            testId="mobile-workflow-instance-nodes"
                            isEmpty={nodes.length === 0}
                            empty="This workflow has no node."
                        >
                            {
                                nodes.map(node =>
                                    <li
                                        key={node.id}
                                        className="ot-mobile-row ot-mobile-row-stacked"
                                        data-testid={`mobile-workflow-node-${node.id}`}
                                    >
                                        <div className="ot-mobile-row-text">
                                            <span className="ot-mobile-row-name">
                                                {node.description || node.id}
                                            </span>
                                            {/*
                                              The executor, which is what says
                                              what the node actually does - the
                                              id is frequently only a label.
                                            */}
                                            <span className="ot-mobile-row-context">
                                                {node.executorId}
                                            </span>
                                            <span className="ot-mobile-row-context ot-mobile-inline">
                                                <WorkflowInstanceNodeStatus status={node.execution?.status}/>
                                                {
                                                    node.execution?.durationMs !== undefined &&
                                                    node.execution?.durationMs !== null &&
                                                    <DurationMs ms={node.execution.durationMs}/>
                                                }
                                            </span>
                                            {
                                                /*
                                                 * Every failed node, not only the
                                                 * first: there is no side panel on
                                                 * a phone to go and find the others
                                                 * in.
                                                 */
                                                node.execution?.error &&
                                                <Alert
                                                    type="error"
                                                    showIcon
                                                    message={node.execution.error}
                                                    data-testid={`mobile-workflow-error-${node.id}`}
                                                />
                                            }
                                        </div>
                                    </li>
                                )
                            }
                        </MobileSectionList>
                    </div>
                }
            </MobileAsyncContent>
        </MobileScreen>
    )
}

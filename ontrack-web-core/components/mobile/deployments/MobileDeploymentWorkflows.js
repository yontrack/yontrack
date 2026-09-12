"use client"

/**
 * The slot's workflows, on a deployment screen.
 *
 * #1736 leaves a phone showing a deployment blocked by a workflow, naming the
 * reason in that workflow's own words - *"Workflow is running"* - with no way to
 * see which workflow, what it is doing, or where it got stuck. This is that way.
 *
 * **All three triggers, always, and not only the ones the current status has
 * reached.** A slot workflow is configuration as much as state: `CONTEXT.md` is
 * explicit that it is drawn whether or not it ever ran, and that a `CANDIDATE`
 * workflow which never ran "is not dormant, it is why nothing has ever deployed
 * there". Hiding the ones whose turn has not come hides exactly that, so one that
 * has not run reads **Not started** rather than being absent.
 *
 * **Its own section rather than rows in Checks.** The Checks list exists so a
 * user can *Answer* and *Override*; a workflow row offers neither, and the two
 * speak different vocabularies - `check.ok` renders "Passed"/"Blocking" while an
 * instance has five statuses. Putting them together would make the one list whose
 * purpose is action half inert.
 *
 * **Absent, not empty, on a slot with no workflow** - as *Deployments in
 * progress* is on the build screen. Most slots have none, and an empty section on
 * every deployment screen in the product to serve the minority that has one is
 * pure cost.
 *
 * **Read-only.** No override, no stop. Overriding a blocking workflow needs
 * `SlotUpdate` *and* `SlotPipelineOverride` - a pair `PROJECT_ROLE_PIPELINES_MANAGER`
 * does not hold - so the button would be absent for the very role this screen is
 * built around. **Showing that a workflow *was* overridden is not read-only's
 * opposite**: read-only is a decision about what the phone lets you *do*, not
 * about what it lets you *know*, and a row reading `Error` with no sign that a
 * human deliberately waved it through would be actively misleading - the same
 * reason #1736 insists a cancelled deployment shows its reason.
 *
 * The override is drawn as **text** and not as the desktop's
 * `SlotPipelineOverrideIndicator`, which is a hover popover: a phone has no
 * hover, and the rule rows on this same screen already spell theirs out.
 */

import Link from "next/link"
import {gql} from "graphql-request"
import {Typography} from "antd"
import {FaProjectDiagram, FaRegHourglass} from "react-icons/fa"
import MobileSectionList from "@components/mobile/layout/MobileSectionList"
import SlotWorkflowTrigger from "@components/extension/environments/SlotWorkflowTrigger"
import WorkflowInstanceStatus from "@components/extension/workflows/WorkflowInstanceStatus"
import TimestampText from "@components/common/TimestampText"
import DurationMs from "@components/common/DurationMs"
import {mobileWorkflowInstanceUri} from "@components/mobile/mobileRoutes"

/**
 * One configured slot workflow and its run for *this* pipeline.
 *
 * Neither `Slot.workflows` nor `slotWorkflowInstanceForPipeline` is inside the
 * licence-gated region - the environments licence gates field *contributors* like
 * `Build.slotPipelines`, not these types - so this rides in the deployment
 * screen's own query rather than needing the `useMobileDeployments` split. They
 * are protected at runtime by `checkSlotAccess<SlotView>`, which the screen
 * already satisfies.
 */
export const gqlMobileSlotWorkflow = gql`
    fragment MobileSlotWorkflow on SlotWorkflow {
        id
        trigger
        workflow {
            name
        }
        slotWorkflowInstanceForPipeline(pipelineId: $id) {
            id
            overridden
            override {
                user
                message
            }
            workflowInstance {
                id
                status
                startTime
                durationMs
                finished
            }
        }
    }
`

/**
 * The workflows of a slot, in the order a deployment meets them.
 *
 * @param {object} [slot] The deployment's slot, with its three aliased trigger
 *   lists. Absent until the first answer lands.
 * @returns {Array} One entry per configured workflow, candidate first.
 */
export const orderedSlotWorkflows = (slot) => [
    ...(slot?.candidateWorkflows ?? []),
    ...(slot?.runningWorkflows ?? []),
    ...(slot?.doneWorkflows ?? []),
]

export default function MobileDeploymentWorkflows({deployment}) {

    const workflows = orderedSlotWorkflows(deployment?.slot)

    // Absent rather than empty: most slots declare none.
    if (workflows.length === 0) return null

    return (
        <MobileSectionList
            title="Workflows"
            testId="mobile-deployment-workflows"
            isEmpty={false}
        >
            {
                workflows.map(slotWorkflow => {
                    const slotInstance = slotWorkflow.slotWorkflowInstanceForPipeline
                    const instance = slotInstance?.workflowInstance
                    return (
                        <li
                            key={slotWorkflow.id}
                            className="ot-mobile-row ot-mobile-row-stacked"
                            data-testid={`mobile-deployment-workflow-${slotWorkflow.id}`}
                        >
                            <div className="ot-mobile-row-text">
                                <span className="ot-mobile-row-name ot-mobile-inline">
                                    <FaProjectDiagram aria-hidden="true"/>
                                    {
                                        /*
                                         * A workflow that never ran has no run to
                                         * open, so it is a name and not a dead
                                         * link - the same rule `MobileEntityRow`
                                         * keeps: a tap that 404s is worse than a
                                         * row that does not move.
                                         */
                                        instance ?
                                            <Link
                                                href={mobileWorkflowInstanceUri(instance.id)}
                                                data-testid={`mobile-deployment-workflow-link-${slotWorkflow.id}`}
                                            >
                                                {slotWorkflow.workflow?.name}
                                            </Link> :
                                            slotWorkflow.workflow?.name
                                    }
                                </span>
                                <span className="ot-mobile-row-context ot-mobile-inline">
                                    {/* Which of the three triggers this one is on. */}
                                    <SlotWorkflowTrigger trigger={slotWorkflow.trigger}/>
                                    {
                                        instance ?
                                            <WorkflowInstanceStatus
                                                id={`mobile-deployment-workflow-status-${slotWorkflow.id}`}
                                                status={instance.status}
                                            /> :
                                            <span data-testid={`mobile-deployment-workflow-status-${slotWorkflow.id}`}>
                                                <FaRegHourglass aria-hidden="true"/>
                                                {' Not started'}
                                            </span>
                                    }
                                </span>
                                {
                                    instance &&
                                    <span className="ot-mobile-row-context">
                                        <TimestampText value={instance.startTime} prefix="started" relative/>
                                        {
                                            // Only once it is over: a duration on a
                                            // running workflow is the age of a
                                            // snapshot, not how long it took.
                                            instance.finished && instance.durationMs !== undefined &&
                                            instance.durationMs !== null &&
                                            <>
                                                {' — '}
                                                <DurationMs ms={instance.durationMs}/>
                                            </>
                                        }
                                    </span>
                                }
                                {
                                    slotInstance?.overridden &&
                                    <Typography.Text
                                        type="warning"
                                        className="ot-mobile-row-context"
                                        data-testid={`mobile-deployment-workflow-overridden-${slotWorkflow.id}`}
                                    >
                                        {
                                            `Overridden${slotInstance.override?.user
                                                ? ` by ${slotInstance.override.user}`
                                                : ''}`
                                        }
                                        {
                                            slotInstance.override?.message &&
                                            `. ${slotInstance.override.message}`
                                        }
                                    </Typography.Text>
                                }
                            </div>
                        </li>
                    )
                })
            }
        </MobileSectionList>
    )
}

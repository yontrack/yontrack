import {gql} from "graphql-request"

/**
 * What every shared component needs to know about a build.
 *
 * `promotionRuns(lastPerLevel: true)` comes back in the branch's own promotion order, lowest rung
 * first, so the *last* entry is the top promotion - see `topPromotionRun` in `slotCellModel`.
 *
 * `displayName` and not `releaseProperty { value }`: the property's value is JSON, and every screen
 * asking for it had to know its shape to get a name out of it - which is how #1824 put
 * `[object Object]` on the matrix, the drawer and the deploy dialog. `displayName` is a non-null
 * String the server has already resolved, falling back to `name`, so there is no shape left to get
 * wrong. `name` stays as the label's fallback, and for the few sentences that name the build number
 * deliberately - "Deployment #12 (build 105, RUNNING) will be cancelled."
 */
export const gqlSharedBuildData = gql`
    fragment SharedBuildData on Build {
        id
        name
        displayName
        branch {
            id
            name
        }
        promotionRuns(lastPerLevel: true) {
            id
            promotionLevel {
                id
                name
                description
                image
            }
        }
    }
`

/**
 * One deployment, as the cell, the drawer's "Recent" list and the journey chip read it.
 */
export const gqlSharedDeploymentData = gql`
    fragment SharedDeploymentData on SlotPipeline {
        id
        number
        status
        finished
        start
        end
        build {
            ...SharedBuildData
        }
    }
    ${gqlSharedBuildData}
`

/**
 * Everything a slot cell draws, and nothing else.
 *
 * It is one fragment rather than several because the matrix asks it of every slot of every visible
 * project in a single query: the cell is the unit the server is asked about.
 */
export const gqlSlotCellData = gql`
    fragment SlotCellData on Slot {
        id
        qualifier
        blocked
        behind
        environment {
            id
            name
            order
            image
        }
        project {
            id
            name
        }
        lastDeployedPipeline {
            ...SharedDeploymentData
        }
        currentPipeline {
            ...SharedDeploymentData
        }
    }
    ${gqlSharedDeploymentData}
`

/**
 * The admission rules of a deployment, with their verdict - what "What's blocking" is made of.
 */
export const gqlSharedAdmissionRuleData = gql`
    fragment SharedAdmissionRuleData on SlotPipelineAdmissionRuleStatus {
        canBeOverridden
        overridden
        check {
            ok
            reason
        }
        override {
            user
            timestamp
            message
        }
        # What somebody answered this rule, and who. Only the rules that ask for an answer have any
        # - the manual approval is the one in the box today - and that is what lets "What's
        # blocking" show who approved and what they wrote rather than only that it was approved.
        data {
            user
            timestamp
            data
        }
        admissionRuleConfig {
            id
            name
            description
            ruleId
            ruleConfig
        }
    }
`

/**
 * One slot workflow and its run for this deployment - the workflow half of "What's blocking".
 *
 * `slotWorkflowInstanceForPipeline` needs the deployment's id, so any query using this fragment
 * declares a `$pipelineId: String!` variable.
 */
export const gqlSharedSlotWorkflowData = gql`
    fragment SharedSlotWorkflowData on SlotWorkflow {
        id
        trigger
        workflow {
            name
        }
        slotWorkflowInstanceForPipeline(pipelineId: $pipelineId) {
            id
            canBeOverridden
            overridden
            check {
                ok
                reason
            }
            override {
                user
                timestamp
                message
            }
            workflowInstance {
                id
                status
                startTime
                endTime
                durationMs
            }
        }
    }
`

/**
 * A build's state in one slot.
 *
 * `environment` carries `order` and `image` so the chip can draw the environment's icon from what
 * it already has - see `EnvironmentImage`. `authorizations` is on the slot rather than on the build
 * because the right to deploy is granted per project and read per slot, and the journey strip's own
 * Deploy button is shown only to somebody who has it *somewhere* in this project.
 */
export const gqlBuildJourneyData = gql`
    fragment BuildJourneyData on BuildSlotJourney {
        state
        nonEligibleRules {
            id
            name
            ruleId
            ruleConfig
        }
        pipeline {
            id
            number
            status
        }
        slot {
            id
            qualifier
            environment {
                id
                name
                order
                image
            }
            project {
                id
                name
            }
            authorizations {
                name
                action
                authorized
            }
        }
    }
`

/**
 * Cancelling whatever is in flight.
 *
 * The reason is an argument rather than a constant because it lands in the deployment's audit
 * trail: a cancellation recorded as coming from the drawer when it came from the deployment page
 * is the sort of small untruth that makes an audit trail not worth reading.
 */
export const gqlSlotCancel = gql`
            mutation SlotCancel($id: String!, $reason: String!) {
                cancelSlotPipeline(input: {pipelineId: $id, reason: $reason}) {
                    errors { message }
                }
            }
        `

/**
 * Completing a running deployment.
 */
export const gqlSlotDrawerFinish = gql`
            mutation SlotDrawerFinish($id: String!) {
                finishSlotPipelineDeployment(input: {pipelineId: $id, forcing: false, message: null}) {
                    finishStatus { ok message }
                    errors { message }
                }
            }
        `

/**
 * Moving a candidate into running.
 */
export const gqlSlotDrawerRun = gql`
            mutation SlotDrawerRun($id: String!) {
                startSlotPipelineDeployment(input: {pipelineId: $id}) {
                    deploymentStatus { ok message }
                    errors { message }
                }
            }
        `

/**
 * The in-flight deployment, which needs its own id and so cannot be part of the query above.
 */
export const gqlSlotDrawerDeployment = gql`
            query SlotDrawerDeployment($pipelineId: String!) {
                slotPipelineById(id: $pipelineId) {
                    id
                    status
                    errorMessage
                    runAction {
                        ok
                        successCount
                        totalCount
                    }
                    finishAction {
                        ok
                        successCount
                        totalCount
                    }
                    requiredInputs {
                        config {
                            id
                        }
                    }
                    admissionRules {
                        ...SharedAdmissionRuleData
                    }
                    slot {
                        id
                        authorizations {
                            name
                            action
                            authorized
                        }
                        candidateWorkflows: workflows(trigger: CANDIDATE) {
                            ...SharedSlotWorkflowData
                        }
                        runningWorkflows: workflows(trigger: RUNNING) {
                            ...SharedSlotWorkflowData
                        }
                    }
                }
            }
            ${gqlSharedAdmissionRuleData}
            ${gqlSharedSlotWorkflowData}
        `

/**
 * The slot's own sections of the drawer: Now, Next and Recent.
 */
export const gqlSlotDrawer = gql`
            query SlotDrawer($slotId: String!) {
                slotById(id: $slotId) {
                    id
                    qualifier
                    behind
                    environment {
                        id
                        name
                        order
                    }
                    project {
                        id
                        name
                    }
                    authorizations {
                        name
                        action
                        authorized
                    }
                    lastDeployedPipeline {
                        ...SharedDeploymentData
                        lastChange {
                            user
                            timestamp
                        }
                    }
                    nextBuilds(count: 3) {
                        ...SharedBuildData
                    }
                    pipelines(size: 5) {
                        pageItems {
                            ...SharedDeploymentData
                        }
                    }
                }
            }
            # SharedDeploymentData already carries SharedBuildData, which nextBuilds also uses.
            # Interpolating both would define the same fragment twice and the server would refuse
            # the whole document.
            ${gqlSharedDeploymentData}
        `

/**
 * The drawer's header, asked for on its own so the drawer has a name before the rest of it lands.
 */
export const gqlSlotDrawerTitle = gql`
            query SlotDrawerTitle($slotId: String!) {
                slotById(id: $slotId) {
                    id
                    qualifier
                    environment {
                        id
                        name
                    }
                    project {
                        id
                        name
                    }
                }
            }
        `

/**
 * Starting the deployment, whichever direction the dialog was opened from.
 */
export const gqlDeployDialogStart = gql`
                    mutation DeployDialogStart($slotId: String!, $buildId: Int!) {
                        startSlotPipeline(input: {slotId: $slotId, buildId: $buildId}) {
                            pipeline {
                                id
                            }
                            errors {
                                message
                            }
                        }
                    }
                `

/**
 * From a slot: the deployable builds that could come here.
 */
export const gqlDeployDialogBuilds = gql`
            query DeployDialogBuilds($slotId: String!) {
                slotById(id: $slotId) {
                    id
                    authorizations {
                        name
                        action
                        authorized
                    }
                    currentPipeline {
                        id
                        number
                        status
                        finished
                        build {
                            id
                            name
                        }
                    }
                    # Deployable, so every row offered is one the slot's rules already accept.
                    # See buildChoices for why this direction does not explain refusals.
                    eligibleBuilds(size: 5, deployable: true) {
                        pageItems {
                            ...SharedBuildData
                        }
                    }
                }
            }
            ${gqlSharedBuildData}
        `

/**
 * From a build: every slot of its project, with the rules refusing this build, and the ones which
 * would keep its deployment waiting as a candidate.
 */
export const gqlDeployDialogSlots = gql`
            query DeployDialogSlots($buildId: Int!) {
                eligibleSlotsForBuild(buildId: $buildId) {
                    eligible
                    nonEligibleRules {
                        id
                        name
                        ruleId
                        ruleConfig
                    }
                    # Eligible but not deployable yet: the deployment would wait as a candidate
                    deployable
                    nonDeployableRules {
                        rule {
                            id
                            name
                            ruleId
                        }
                        reason
                    }
                    pipelineOnlyRules {
                        id
                        name
                        ruleId
                    }
                    slot {
                        id
                        qualifier
                        environment {
                            id
                            name
                            order
                        }
                        project {
                            id
                            name
                        }
                        authorizations {
                            name
                            action
                            authorized
                        }
                        currentPipeline {
                            id
                            number
                            status
                            finished
                            build {
                                id
                                name
                            }
                        }
                    }
                }
            }
        `


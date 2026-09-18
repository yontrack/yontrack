import {gql} from "graphql-request"

/**
 * What every shared component needs to know about a build.
 *
 * `promotionRuns(lastPerLevel: true)` comes back in the branch's own promotion order, lowest rung
 * first, so the *last* entry is the top promotion - see `topPromotionRun` in `slotCellModel`.
 */
export const gqlSharedBuildData = gql`
    fragment SharedBuildData on Build {
        id
        name
        releaseProperty {
            value
        }
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
            }
            project {
                id
                name
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
                        admissionRuleConfig {
                            id
                            name
                            description
                            ruleId
                            ruleConfig
                        }
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
 * From a build: every slot of its project, with the rules refusing this build.
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


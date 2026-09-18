import {gql} from "graphql-request"
import {
    gqlSharedAdmissionRuleData,
    gqlSharedBuildData,
    gqlSharedSlotWorkflowData,
} from "@components/extension/environments/shared/environmentsSharedGraphQL"

/**
 * Everything the deployment page draws, in one query.
 *
 * One query and not three, unlike the drawer: the drawer asks about a slot and only then learns
 * which deployment is in flight, so the checks have to wait for a second round trip. Here the
 * deployment's id is in the URL, so `slotWorkflowInstanceForPipeline` and the admission rule checks
 * can all be asked at once - and the page has nothing useful to show until they land anyway, since
 * "what is blocking" is the reason somebody opened it.
 *
 * `$pipelineId` is declared because `SharedSlotWorkflowData` needs it.
 */
export const gqlDeploymentPage = gql`
    query DeploymentPage($pipelineId: String!) {
        slotPipelineById(id: $pipelineId) {
            id
            number
            status
            finished
            start
            end
            errorMessage
            build {
                ...SharedBuildData
            }
            changes {
                id
                user
                timestamp
                type
                status
                message
                overrideMessage
            }
            requiredInputs {
                config {
                    id
                }
            }
            admissionRules {
                ...SharedAdmissionRuleData
            }
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
            slot {
                id
                qualifier
                authorizations {
                    name
                    action
                    authorized
                }
                environment {
                    id
                    name
                    order
                }
                project {
                    id
                    name
                }
                candidateWorkflows: workflows(trigger: CANDIDATE) {
                    ...SharedSlotWorkflowData
                }
                runningWorkflows: workflows(trigger: RUNNING) {
                    ...SharedSlotWorkflowData
                }
                doneWorkflows: workflows(trigger: DONE) {
                    ...SharedSlotWorkflowData
                }
            }
        }
    }
    ${gqlSharedBuildData}
    ${gqlSharedAdmissionRuleData}
    ${gqlSharedSlotWorkflowData}
`

import {useQuery} from "@components/services/useQuery";
import {gql} from "graphql-request";
import {List} from "antd";
import {useEffect, useState} from "react";
import {
    DeploymentCancelledStatusStep,
    DeploymentCandidateStatusStep,
    DeploymentDoneStatusStep,
    DeploymentErrorStep,
    DeploymentRunningStatusStep,
    findChange
} from "@components/extension/environments/deployment/steps/deploymentStatusSteps";
import {
    DeploymentCancelButtonStep,
    DeploymentFinishButtonStep,
    DeploymentRunButtonStep
} from "@components/extension/environments/deployment/steps/deploymentActionSteps";
import {
    CandidateAdmissionRuleStep
} from "@components/extension/environments/deployment/steps/CandidateAdmissionRuleStep";
import {DeploymentWorkflowStep} from "@components/extension/environments/deployment/steps/DeploymentWorkflowStep";

function CandidateStatus({deployment}) {
    return <DeploymentCandidateStatusStep deployment={deployment}/>
}

const deploymentError = (deployment) => {
    return <DeploymentErrorStep deployment={deployment}/>
}

const candidateAdmissionRules = (pipeline, disabled = true, onChange) => {
    return pipeline.admissionRules.map(rule =>
        <CandidateAdmissionRuleStep
            key={rule.admissionRuleConfig.id}
            pipeline={pipeline}
            rule={rule}
            disabled={disabled}
            onChange={onChange}
        />
    )
}

const candidateWorkflows = (pipeline, onChange, disabled = false) => {
    return pipeline.slot.candidateWorkflows.map(slotWorkflow =>
        <DeploymentWorkflowStep key={slotWorkflow.id} deployment={pipeline} slotWorkflow={slotWorkflow} disabled={disabled} onChange={onChange}/>
    )
}

const runButton = (deployment, reloadState, onChange) => {
    return <DeploymentRunButtonStep deployment={deployment} reloadState={reloadState} onChange={onChange}/>
}

function RunningStatus({deployment}) {
    return <DeploymentRunningStatusStep deployment={deployment}/>
}

const runningWorkflows = (pipeline, onChange, disabled = false) => {
    return pipeline.slot.runningWorkflows.map(slotWorkflow =>
        <DeploymentWorkflowStep key={slotWorkflow.id} deployment={pipeline} slotWorkflow={slotWorkflow} onChange={onChange} disabled={disabled}/>
    )
}

const finishButton = (deployment, reloadState, onChange) => {
    return <DeploymentFinishButtonStep deployment={deployment} reloadState={reloadState} onChange={onChange}/>
}

function FinishStatus({deployment}) {
    return <DeploymentDoneStatusStep deployment={deployment}/>
}

const doneWorkflows = (pipeline, onChange) => {
    return pipeline.slot.doneWorkflows.map(slotWorkflow =>
        <DeploymentWorkflowStep key={slotWorkflow.id} deployment={pipeline} slotWorkflow={slotWorkflow} onChange={onChange} disabled={true}/>
    )
}

const cancelButton = (deployment, reloadState, onChange) => {
    return <DeploymentCancelButtonStep deployment={deployment} reloadState={reloadState} onChange={onChange}/>
}

function CancelledStatus({deployment}) {
    return <DeploymentCancelledStatusStep deployment={deployment}/>
}

const generateItems = (pipeline, reloadState, onChange) => {

    // 1 - Candidate

    if (pipeline.status === 'CANDIDATE') {
        const items = [
            <CandidateStatus key="status" deployment={pipeline}/>,
            deploymentError(pipeline),
            ...candidateAdmissionRules(pipeline, false, onChange),
            ...candidateWorkflows(pipeline, onChange),
        ]
        if (pipeline.runAction) {
            items.push(runButton(pipeline, reloadState, onChange))
        }
        items.push(cancelButton(pipeline, reloadState, onChange))
        return items
    }

    // 2 - Running

    else if (pipeline.status === 'RUNNING') {
        const items = [
            <CandidateStatus key="status-candidate" deployment={pipeline}/>,
            ...candidateAdmissionRules(pipeline),
            ...candidateWorkflows(pipeline, onChange, true),
            <RunningStatus key="status-running" deployment={pipeline}/>,
            deploymentError(pipeline),
            ...runningWorkflows(pipeline, onChange),
        ]
        if (pipeline.finishAction) {
            items.push(finishButton(pipeline, reloadState, onChange))
        }
        items.push(cancelButton(pipeline, reloadState, onChange))
        return items
    }

    // 3 - Cancelled

    else if (pipeline.status === 'CANCELLED') {
        const items = [
            <CandidateStatus key="status-candidate" deployment={pipeline}/>,
            ...candidateAdmissionRules(pipeline),
            ...candidateWorkflows(pipeline, onChange, true),
        ]
        const runningChange = findChange(pipeline, 'RUNNING')
        if (runningChange) {
            items.push(
                <RunningStatus key="status-running" deployment={pipeline}/>,
                ...runningWorkflows(pipeline, onChange, true),
            )
        }
        items.push(<CancelledStatus key="status-cancelled" deployment={pipeline}/>)
        items.push(deploymentError(pipeline))
        return items
    }

    // 4 - Done

    else if (pipeline.status === 'DONE') {
        return [
            <CandidateStatus key="status-candidate" deployment={pipeline}/>,
            ...candidateAdmissionRules(pipeline),
            ...candidateWorkflows(pipeline, onChange, true),
            <RunningStatus key="status-running" deployment={pipeline}/>,
            ...runningWorkflows(pipeline, onChange, true),
            <FinishStatus key="status-done" deployment={pipeline}/>,
            deploymentError(pipeline),
            ...doneWorkflows(pipeline, onChange),
        ]
    }

    // Unknown

    else {
        return []
    }

}

export default function SlotPipelineSteps({pipelineId, reloadState, onChange}) {

    const {loading, data} = useQuery(
        gql`

            fragment SlotWorkflowContent on SlotWorkflow {
                id
                workflow {
                    name
                }
            }

            fragment SlotWorkflowInstanceContent on SlotWorkflowInstance {
                id
                check {
                    ok
                    reason
                }
                canBeOverridden
                overridden
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

            query SlotPipelineSteps($pipelineId: String!) {
                slotPipelineById(id: $pipelineId) {
                    id
                    status
                    errorMessage
                    changes {
                        user
                        timestamp
                        type
                        message
                        status
                        overrideMessage
                    }
                    admissionRules {
                        check {
                            ok
                            reason
                        }
                        admissionRuleConfig {
                            id
                            name
                            description
                            ruleId
                            ruleConfig
                        }
                        data {
                            user
                            timestamp
                            data
                        }
                        canBeOverridden
                        overridden
                        override {
                            user
                            timestamp
                            message
                        }
                    }
                    slot {
                        candidateWorkflows: workflows(trigger: CANDIDATE) {
                            ...SlotWorkflowContent
                            slotWorkflowInstanceForPipeline(pipelineId: $pipelineId) {
                                ...SlotWorkflowInstanceContent
                            }
                        }
                        runningWorkflows: workflows(trigger: RUNNING) {
                            ...SlotWorkflowContent
                            slotWorkflowInstanceForPipeline(pipelineId: $pipelineId) {
                                ...SlotWorkflowInstanceContent
                            }
                        }
                        doneWorkflows: workflows(trigger: DONE) {
                            ...SlotWorkflowContent
                            slotWorkflowInstanceForPipeline(pipelineId: $pipelineId) {
                                ...SlotWorkflowInstanceContent
                            }
                        }
                    }
                    runAction {
                        ok
                        overridden
                        successCount
                        totalCount
                        percentage
                    }
                    finishAction {
                        ok
                        overridden
                        successCount
                        totalCount
                        percentage
                    }
                }
            }
        `,
        {
            variables: {pipelineId},
            deps: [reloadState],
        }
    )

    const [items, setItems] = useState([])
    useEffect(() => {
        if (data) {
            const pipeline = data.slotPipelineById
            const items = generateItems(pipeline, reloadState, onChange)
            setItems(items)
        }
    }, [data])

    return (
        <>
            <List
                itemLayout="horizontal"
                loading={loading}
            >
                {items}
            </List>
        </>
    )
}
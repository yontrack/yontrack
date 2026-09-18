import {Collapse, Typography} from "antd"
import WhatsBlocking from "@components/extension/environments/shared/WhatsBlocking"
import {checksSummary} from "@components/extension/environments/shared/whatsBlockingModel"
import {earlierPhases, isSettled} from "@components/extension/environments/deployment/deploymentModel"

/**
 * The phases this deployment has already been through, below "What's blocking".
 *
 * They are collapsed and read-only while the deployment is still moving: a candidate phase which
 * passed is history, and history behind a summary line ("2 checks passed") is the reassurance
 * somebody wants without being the thing they have to read past to find the failing check.
 *
 * A **finished** deployment is the other way round - it has no current phase, so there is nothing
 * to read past, and the record is the whole point of the page. Every phase is expanded, still
 * read-only, which is what the auditor opening a two-month-old deployment came for.
 *
 * @param {Object} deployment The deployment.
 */
export default function DeploymentPhases({deployment}) {

    const phases = earlierPhases(deployment)
    if (phases.length === 0) return null

    const settled = isSettled(deployment)

    return (
        <Collapse
            ghost
            size="small"
            data-testid="deployment-phases"
            defaultActiveKey={settled ? phases.map(phase => phase.phase) : []}
            items={phases.map(phase => {
                const summary = checksSummary(phase.items)
                return {
                    key: phase.phase,
                    label: <span data-testid={`deployment-phase-label-${phase.phase}`}>
                        <Typography.Text>{phase.title}</Typography.Text>
                        {' '}
                        <Typography.Text type="secondary">({summary.text})</Typography.Text>
                    </span>,
                    children: <WhatsBlocking
                        deployment={deployment}
                        phase={phase.phase}
                        actions={false}
                        testId={`deployment-phase-${phase.phase}`}
                    />,
                }
            })}
        />
    )
}

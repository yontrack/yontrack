import {useState} from "react"
import Head from "next/head"
import {Alert, Card, Col, Row, Skeleton, Space} from "antd"
import {pageTitle} from "@components/common/Titles"
import MainPage from "@components/layouts/MainPage"
import PageSection from "@components/common/PageSection"
import {CloseCommand} from "@components/common/Commands"
import {callGraphQL, useQuery} from "@components/services/GraphQL"
import {slotBreadcrumbs, slotTitle, slotUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import DeleteDeploymentCommand from "@components/extension/environments/DeleteDeploymentCommand"
import ForceDeploymentCommand from "@components/extension/environments/ForceDeploymentCommand"
import WhatsBlocking from "@components/extension/environments/shared/WhatsBlocking"
import {useFreshness} from "@components/extension/environments/shared/Freshness"
import {slotDisplayName} from "@components/extension/environments/shared/slotCellModel"
import {
    gqlSlotCancel,
    gqlSlotDrawerFinish,
    gqlSlotDrawerRun,
} from "@components/extension/environments/shared/environmentsSharedGraphQL"
import {gqlDeploymentPage} from "@components/extension/environments/deployment/deploymentGraphQL"
import DeploymentHeader from "@components/extension/environments/deployment/DeploymentHeader"
import DeploymentPhases from "@components/extension/environments/deployment/DeploymentPhases"
import DeploymentTimeline from "@components/extension/environments/deployment/DeploymentTimeline"

/**
 * The deployment page - job 3 of the redesign, "understand why a deployment is blocked".
 *
 * What it replaces is one `List` whose shape changed with the status, holding every rule and every
 * workflow of every phase with equal weight, plus a `Descriptions` summary above it. Finding the
 * failing check meant reading the whole list and knowing which phase mattered.
 *
 * The page is now three answers in three places:
 *
 * 1. **The header** - what is being deployed, where it got to (a `Steps` bar), and the *one* action
 *    that would move it.
 * 2. **What's blocking** - the current phase only, failing items first, the fix on the failing row.
 *    The same component the drawer uses, so the two screens cannot describe one deployment
 *    differently.
 * 3. **The timeline** - `SlotPipeline.changes`, newest first, including the override messages which
 *    never reached a screen before.
 *
 * It polls every 30 seconds like the other operational screens: somebody watching a deployment run
 * is the exact case where a silently stale page misleads.
 *
 * @param {string} id The deployment's id, from the route.
 */
export default function DeploymentView({id}) {

    const {refreshCount, refreshedAt, refresh} = useFreshness()

    const [acting, setActing] = useState(false)
    const [actionError, setActionError] = useState(null)

    const {data, loading, finished, error} = useQuery(
        gqlDeploymentPage,
        {
            variables: {pipelineId: id},
            deps: [id, refreshCount],
            condition: !!id,
            dataFn: data => data.slotPipelineById,
        }
    )

    const deployment = data

    const act = async (mutation, userNode, statusNode, variables = {}) => {
        setActing(true)
        setActionError(null)
        try {
            const result = await callGraphQL({query: mutation, variables: {id, ...variables}})
            const payload = result?.[userNode]
            const errors = payload?.errors
            if (errors && errors.length > 0) {
                setActionError(errors[0].message)
            } else if (statusNode && payload?.[statusNode]?.ok === false) {
                setActionError(payload[statusNode].message)
            } else {
                refresh()
            }
        } catch (ex) {
            setActionError(ex.message)
        } finally {
            setActing(false)
        }
    }

    const title = deployment
        ? `Deployment #${deployment.number} — ${slotDisplayName(deployment.slot)}`
        : 'Deployment'

    const headTitle = deployment
        ? `${slotTitle(deployment.slot)} | #${deployment.number}`
        : 'Deployment'

    /*
     * Built in the render body rather than kept in state: they are a function of the answer and of
     * nothing else, and commands filled in by an effect appear a tick after the header they belong
     * to, which reads as the page still loading when it is not.
     */
    const commands = deployment ? [
        <ForceDeploymentCommand key="force" deployment={deployment} onForced={refresh}/>,
        <DeleteDeploymentCommand key="delete" deployment={deployment}/>,
        <CloseCommand key="close" href={slotUri(deployment.slot)}/>,
    ] : []

    return (
        <>
            <Head>
                {pageTitle(headTitle)}
            </Head>
            <MainPage
                title={title}
                breadcrumbs={deployment ? slotBreadcrumbs(deployment.slot) : []}
                commands={commands}
            >
                {
                    error &&
                    <Alert type="error" showIcon message="Could not load the deployment."
                           description={error} data-testid="deployment-load-error"/>
                }
                {
                    !error && (loading || !finished) && !deployment &&
                    <Skeleton active paragraph={{rows: 6}}/>
                }
                {
                    !error && finished && !deployment &&
                    <Alert type="warning" showIcon message="No such deployment."
                           data-testid="deployment-missing"/>
                }
                {
                    deployment &&
                    <Space direction="vertical" size={16} className="ot-line"
                           data-testid={`deployment-${deployment.id}`}>

                        <Card size="small">
                            <DeploymentHeader
                                deployment={deployment}
                                acting={acting}
                                onStart={() => act(gqlSlotDrawerRun, 'startSlotPipelineDeployment', 'deploymentStatus')}
                                onFinish={() => act(gqlSlotDrawerFinish, 'finishSlotPipelineDeployment', 'finishStatus')}
                                onCancel={() => act(
                                    gqlSlotCancel,
                                    'cancelSlotPipeline',
                                    null,
                                    {reason: "Cancelled from the deployment page."},
                                )}
                                refreshedAt={refreshedAt}
                                refresh={refresh}
                            />
                        </Card>

                        {
                            actionError &&
                            <Alert type="error" showIcon message={actionError}
                                   data-testid="deployment-action-error"/>
                        }

                        <Row gutter={16}>
                            <Col span={16}>
                                <PageSection title="What's blocking" padding={true}>
                                    <Space direction="vertical" size={16} className="ot-line">
                                        <WhatsBlocking deployment={deployment} onChange={refresh}/>
                                        <DeploymentPhases deployment={deployment}/>
                                    </Space>
                                </PageSection>
                            </Col>
                            <Col span={8}>
                                <PageSection title="Timeline" padding={true}>
                                    <DeploymentTimeline deployment={deployment}/>
                                </PageSection>
                            </Col>
                        </Row>
                    </Space>
                }
            </MainPage>
        </>
    )
}

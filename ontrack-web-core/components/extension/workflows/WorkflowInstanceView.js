import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import MainPage from "@components/layouts/MainPage";
import Link from "next/link";
import {useQuery} from "@components/services/GraphQL";
import {useContext, useState} from "react";
import {Descriptions, Skeleton, Space, Typography} from "antd";
import {gql} from "graphql-request";
import TimestampText from "@components/common/TimestampText";
import DurationMs from "@components/common/DurationMs";
import WorkflowInstanceStatus from "@components/extension/workflows/WorkflowInstanceStatus";
import WorkflowInstanceGraph from "@components/extension/workflows/WorkflowInstanceGraph";
import PageSection from "@components/common/PageSection";
import WorkflowNodeExecutorContextProvider from "@components/extension/workflows/WorkflowNodeExecutorContext";
import {UserContext} from "@components/providers/UserProvider";
import WorkflowInstanceStopButton from "@components/extension/workflows/WorkflowInstanceStopButton";
import {AutoRefreshButton, AutoRefreshContextProvider} from "@components/common/AutoRefresh";
import TriggerComponent from "@components/framework/trigger/TriggerComponent";
import WorkflowInstanceContexts from "@components/extension/workflows/WorkflowInstanceContexts";

const EMPTY_INSTANCE = {
    id: '',
    workflow: {
        name: ''
    }
}

// Orders the answers of the initial load and of the auto-refresh, whichever came last
let sequence = 0
const nextSequence = () => ++sequence

export default function WorkflowInstanceView({id}) {

    const user = useContext(UserContext)

    const [loadingCount, setLoadingCount] = useState(0)

    const reload = () => {
        setLoadingCount(count => count + 1)
    }

    const {data: loadedInstance, loading, finished} = useQuery(
        gql`
            query WorkflowInstance($id: String!) {
                workflowInstance(id: $id) {
                    id
                    timestamp
                    status
                    finished
                    startTime
                    endTime
                    durationMs
                    workflow {
                        name
                        nodes {
                            id
                            executorId
                            timeout
                            data
                            parents {
                                id
                            }
                        }
                    }
                    event {
                        values {
                            name
                            value
                        }
                    }
                    triggerData {
                        id
                        data
                    }
                    contexts {
                        name
                        contextData {
                            id
                            data
                        }
                    }
                    nodesExecutions {
                        id
                        status
                        output
                        error
                        startTime
                        endTime
                        durationMs
                    }
                }
            }
        `,
        {
            variables: {id},
            deps: [id, loadingCount],
            condition: !!id,
            dataFn: data => ({
                instance: data.workflowInstance,
                sequence: nextSequence(),
            }),
        }
    )
    const instance = loadedInstance?.instance ?? EMPTY_INSTANCE

    // Auto-refresh of the node executions and of the instance's volatile fields. The counter is
    // incremented from a callback which the auto-refresh timer keeps from an earlier render, hence
    // the functional update.
    const [refreshCount, setRefreshCount] = useState(0)
    const reloadInstanceNodeExecutions = () => {
        setRefreshCount(count => count + 1)
    }

    const {data: refreshedInstance} = useQuery(
        gql`
            query WorkflowInstanceNodeExecutions($workflowInstanceId: String!) {
                workflowInstance(id: $workflowInstanceId) {
                    endTime
                    durationMs
                    timestamp
                    status
                    nodesExecutions {
                        id
                        status
                        output
                        error
                        startTime
                        endTime
                        durationMs
                    }
                }
            }
        `,
        {
            variables: {
                workflowInstanceId: id,
            },
            deps: [id, refreshCount],
            condition: !!id && refreshCount > 0,
            dataFn: data => ({
                instance: data.workflowInstance,
                sequence: nextSequence(),
            }),
        }
    )
    const instanceNodeExecutions = refreshedInstance?.instance?.nodesExecutions

    // The volatile fields come from whichever of the two queries answered last
    const latestInstance = [loadedInstance, refreshedInstance]
        .filter(it => it?.instance)
        .sort((a, b) => b.sequence - a.sequence)[0]?.instance
    const refreshableInstanceData = latestInstance ? {
        endTime: latestInstance.endTime,
        durationMs: latestInstance.durationMs,
        timestamp: latestInstance.timestamp,
        status: latestInstance.status,
    } : null

    const items = refreshableInstanceData ? [
        {
            key: 'workflow',
            label: 'Workflow',
            children: instance.workflow.name,
            span: 4,
        },
        {
            key: 'id',
            label: 'ID',
            children: <Typography.Text copyable>{instance.id}</Typography.Text>,
            span: 4,
        },
        {
            key: 'status',
            label: 'Status',
            children: <Space>
                <WorkflowInstanceStatus id="workflow-instance-status" status={refreshableInstanceData.status}/>
                <AutoRefreshButton/>
                {
                    user.authorizations.workflow?.stop &&
                    (refreshableInstanceData.status === 'STARTED' || refreshableInstanceData.status === 'RUNNING') &&
                    <WorkflowInstanceStopButton id={instance.id} onStopped={reload}/>
                }
            </Space>,
            span: 4,
        },
        {
            key: 'startTime',
            label: 'Start time',
            children: <TimestampText value={instance.startTime} format="YYYY MMM DD, HH:mm:ss"/>,
            span: 3,
        },
        {
            key: 'endTime',
            label: 'End time',
            children: <TimestampText value={refreshableInstanceData.endTime}
                                     format="YYYY MMM DD, HH:mm:ss"/>,
            span: 3,
        },
        {
            key: 'duration',
            label: 'Duration',
            children: <DurationMs ms={refreshableInstanceData.durationMs}/>,
            span: 3,
        },
        {
            key: 'timestamp',
            label: 'Last update',
            children: <TimestampText value={refreshableInstanceData.timestamp}
                                     format="YYYY MMM DD, HH:mm:ss"/>,
            span: 3,
        },
        {
            key: 'trigger',
            label: 'Trigger',
            children: <>
                {
                    instance.triggerData &&
                    <TriggerComponent triggerData={instance.triggerData}/>
                }
            </>,
            span: 12,
        },
        {
            key: 'contexts',
            label: 'Contexts',
            children: <>
                {
                    instance.contexts && instance.contexts.length > 0 &&
                    <WorkflowInstanceContexts contexts={instance.contexts} />
                }
            </>,
            span: 12,
        }
    ] : []

    return (
        <>
            <Head>
                {pageTitle(`Workflow | ${instance.workflow.name} [${instance.id}]`)}
            </Head>
            <MainPage
                title={`${instance.workflow.name} [${instance.id}]`}
                breadcrumbs={
                    homeBreadcrumbs().concat([
                        <Link key="audit" href="/extension/workflows/audit">Workflows audit</Link>,
                    ])
                }
                commands={[
                    <CloseCommand key="home" href="/extension/workflows/audit"/>
                ]}
            >
                <AutoRefreshContextProvider onRefresh={reloadInstanceNodeExecutions}>
                    <WorkflowNodeExecutorContextProvider>
                        <Skeleton loading={loading || !finished} active>
                            <Space orientation="vertical">
                                <Descriptions
                                    items={items}
                                    column={12}
                                />
                                <PageSection
                                    title={undefined}
                                    padding={false}>
                                    <WorkflowInstanceGraph
                                        instance={instance}
                                        instanceNodeExecutions={instanceNodeExecutions}
                                    />
                                </PageSection>
                            </Space>
                        </Skeleton>
                    </WorkflowNodeExecutorContextProvider>
                </AutoRefreshContextProvider>
            </MainPage>
        </>
    )
}
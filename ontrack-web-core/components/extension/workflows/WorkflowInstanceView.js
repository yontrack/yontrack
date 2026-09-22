import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import MainPage from "@components/layouts/MainPage";
import Link from "next/link";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useContext, useEffect, useState} from "react";
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

export default function WorkflowInstanceView({id}) {

    const client = useGraphQLClient()
    const user = useContext(UserContext)

    const [loading, setLoading] = useState(true)
    const [loadingCount, setLoadingCount] = useState(0)
    const [instance, setInstance] = useState({
        id: '',
        workflow: {
            name: ''
        }
    })
    const [refreshableInstanceData, setRefreshableInstanceData] = useState(null)

    const reload = () => {
        setLoadingCount(count => count + 1)
    }

    const [items, setItems] = useState([])

    useEffect(() => {
        if (client && id) {
            setLoading(true)
            client.request(
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
                {id}
            ).then(data => {
                const instance = data.workflowInstance
                setInstance(instance)
                setRefreshableInstanceData({
                    endTime: instance.endTime,
                    durationMs: instance.durationMs,
                    timestamp: instance.timestamp,
                    status: instance.status,
                })
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, id, loadingCount])

    useEffect(() => {
        if (instance && refreshableInstanceData) {
            setItems([
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
            ])
        }
    }, [instance, refreshableInstanceData])

    const [instanceNodeExecutions, setInstanceNodeExecutions] = useState()

    const reloadInstanceNodeExecutions = () => {
        client.request(
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
                workflowInstanceId: instance.id,
            }
        ).then(data => {
            setInstanceNodeExecutions(data.workflowInstance.nodesExecutions)
            setRefreshableInstanceData({
                endTime: data.workflowInstance.endTime,
                durationMs: data.workflowInstance.durationMs,
                timestamp: data.workflowInstance.timestamp,
                status: data.workflowInstance.status,
            })
        })
    }

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
                        <Skeleton loading={loading} active>
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
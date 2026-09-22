import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import SubscriptionCard from "@components/extension/notifications/SubscriptionCard";
import {useEffect, useState} from "react";
import {Popconfirm, Skeleton, Space, Table, Typography} from "antd";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import NotificationRecordDetails from "@components/extension/notifications/NotificationRecordDetails";
import Timestamp from "@components/common/Timestamp";
import NotificationResultType from "@components/extension/notifications/NotificationResultType";
import {CloseCommand, Command} from "@components/common/Commands";
import {subscriptionsLink} from "@components/extension/notifications/SubscriptionsLink";
import {FaTrash} from "react-icons/fa";
import {useDeleteSubscription} from "@components/extension/notifications/DeleteSubscription";
import {useRouter} from "next/router";

const {Column} = Table

export default function SubscriptionView({title, breadcrumbs, entity, name, managePermission, onRenamed}) {

    const client = useGraphQLClient()
    const router = useRouter()

    const {deleteSubscription} = useDeleteSubscription()
    const onDeleteSubscription = async () => {
        await deleteSubscription({entity, name})
        await router.push(subscriptionsLink(entity))
    }


    const [loading, setLoading] = useState(true)
    const [subscription, setSubscription] = useState()
    const [recordings, setRecordings] = useState([])
    const [commands, setCommands] = useState([])

    useEffect(() => {
        if (client) {

            const loadSubscription = async () => {
                setLoading(true)
                try {
                    const data = await client.request(
                        gql`
                            query GetEntitySubscription(
                                $entity: ProjectEntityIDInput,
                                $name: String!,
                            ) {
                                eventSubscriptions(size: 1, filter: {entity: $entity, name: $name}) {
                                    pageItems {
                                        name
                                        channel
                                        channelConfig
                                        keywords
                                        events
                                        disabled
                                        contentTemplate
                                    }
                                }
                            }
                        `,
                        {
                            entity: {type: entity.type, id: Number(entity.id)},
                            name,
                        }
                    )
                    setSubscription(data.eventSubscriptions.pageItems[0])

                    const dataRecordings = await client.request(
                        gql`
                            query GetSubscriptionRecordings(
                                $sourceId: String,
                                $sourceData: JSON,
                            ) {
                                notificationRecords(sourceId: $sourceId, sourceData: $sourceData) {
                                    pageItems {
                                        key: id
                                        source {
                                            id
                                            data
                                        }
                                        channel
                                        channelConfig
                                        event
                                        result {
                                            type
                                            message
                                            output
                                        }
                                        timestamp
                                    }
                                }
                            }
                        `,
                        {
                            sourceId: entity ? 'entity-subscription' : 'global-subscription',
                            sourceData: entity ? {
                                entityType: entity.type,
                                entityId: Number(entity.id),
                                subscriptionName: name,
                            } : {
                                subscriptionName: name,
                            }
                        }
                    )
                    setRecordings(dataRecordings.notificationRecords.pageItems)
                    const commands = []
                    if (managePermission) {
                        commands.push(
                            <Popconfirm
                                key="delete"
                                title="Do you really want to delete this subscription?"
                                onConfirm={onDeleteSubscription}
                            >
                                <div>
                                    <Command
                                        text="Delete subscription"
                                        icon={<FaTrash/>}
                                    />
                                </div>
                            </Popconfirm>
                        )
                    }
                    commands.push(<CloseCommand key="close" href={subscriptionsLink(entity)}/>)
                    setCommands(commands)
                } finally {
                    setLoading(false)
                }
            }

            // noinspection JSIgnoredPromiseFromCall
            loadSubscription()
        }
    }, [client, entity, name])

    return (
        <>
            <Head>
                {pageTitle(title)}
            </Head>
            <MainPage
                title={`Subscription: ${name}`}
                breadcrumbs={breadcrumbs}
                commands={commands}
            >
                <Skeleton active loading={loading}>
                    <Space orientation="vertical" className="ot-line">

                        <SubscriptionCard
                            entity={entity}
                            actions={[]}
                            subscription={subscription}
                            managePermission={managePermission}
                            onRenamed={onRenamed}
                        />

                        <Typography.Title level={5} type="secondary">Recordings</Typography.Title>

                        <Table
                            dataSource={recordings}
                            pagination={false}
                            expandable={{
                                expandedRowRender: (record) => (
                                    <>
                                        <NotificationRecordDetails record={record}/>
                                    </>
                                )
                            }}
                        >

                            <Column
                                key="timestamp"
                                title="Timestamp"
                                render={(_, record) => <Timestamp
                                    value={record.timestamp}
                                    format="YYYY MMM DD, HH:mm:ss"
                                />}
                            />

                            <Column
                                key="result"
                                title="Result"
                                render={(_, record) => <NotificationResultType type={record.result.type}/>}
                            />

                        </Table>
                    </Space>
                </Skeleton>
            </MainPage>
        </>
    )
}
import Head from "next/head";
import {useEffect, useMemo, useState} from "react";
import MainPage from "@components/layouts/MainPage";
import {Col, Empty, Row, Skeleton} from "antd";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {FaPlus} from "react-icons/fa";
import {pageTitle} from "@components/common/Titles";
import {CloseCommand, Command} from "@components/common/Commands";
import SubscriptionDialog, {useSubscriptionDialog} from "@components/extension/notifications/SubscriptionDialog";
import SubscriptionCard from "@components/extension/notifications/SubscriptionCard";
import {useSubscriptionActions} from "@components/extension/notifications/SubscriptionActions";

export default function SubscriptionsView({
                                              title,
                                              breadcrumbs = [],
                                              closeUri = '',
                                              managePermission = false,
                                              viewTitle = "Subscriptions",
                                              additionalFilter = {}
                                          }) {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(true)

    const filter = useMemo(() => ({
        ...additionalFilter,
    }), [additionalFilter])

    const [items, setItems] = useState([])
    const [refresh, setRefresh] = useState(0)

    const reload = () => {
        setRefresh(it => it + 1)
    }

    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    query Subscriptions(
                        $offset: Int!,
                        $size: Int!,
                        $filter: EventSubscriptionFilter!,
                    ) {
                        eventSubscriptions(
                            offset: $offset,
                            size: $size,
                            filter: $filter,
                        ) {
                            pageInfo {
                                nextPage {
                                    offset
                                    size
                                }
                            }
                            pageItems {
                                name
                                channel
                                channelConfig
                                disabled
                                events
                                keywords
                                contentTemplate
                            }
                        }
                    }
                `,
                {
                    offset: 0,
                    size: 100,
                    filter,
                }
            ).then(data => {
                setItems(data.eventSubscriptions.pageItems)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, filter, refresh]);

    const {getActions} = useSubscriptionActions(
        additionalFilter.entity,
        managePermission,
        reload
    )

    const subscriptionDialog = useSubscriptionDialog({
        onSuccess: reload,
        projectEntity: additionalFilter.entity,
    })

    const onCreateSubscription = () => {
        subscriptionDialog.start()
    }

    const [commands, setCommands] = useState([])

    useEffect(() => {
        const commands = []

        if (managePermission) {
            commands.push(
                <Command key="create" icon={<FaPlus/>} action={onCreateSubscription} text="Create subscription"/>
            )
        }

        commands.push(<CloseCommand key="close" href={closeUri}/>)

        setCommands(commands)
    }, [managePermission, closeUri]);

    return (
        <>
            <Head>
                {pageTitle(title)}
            </Head>
            <MainPage
                title={viewTitle}
                breadcrumbs={breadcrumbs}
                commands={commands}
            >
                <Skeleton active loading={loading}>
                    {
                        items.length === 0 &&
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                    }
                    <Row gutter={[16, 16]}>
                        {
                            items.map(item =>
                                <Col key={item.name} xs={24} md={12}>
                                    <SubscriptionCard
                                        subscription={item}
                                        entity={additionalFilter.entity}
                                        actions={getActions(item)}
                                        managePermission={managePermission}
                                        onRenamed={reload}
                                    />
                                </Col>
                            )
                        }
                    </Row>
                </Skeleton>
            </MainPage>
            <SubscriptionDialog subscriptionDialog={subscriptionDialog}/>
        </>
    )
}
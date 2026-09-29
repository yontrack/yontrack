import {gql} from "graphql-request";
import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {homeUri} from "@components/common/Links";
import WebhookCreateCommand from "@components/extension/notifications/webhooks/WebhookCreateCommand";
import {useQuery} from "@components/services/GraphQL";
import {useRefresh} from "@components/common/RefreshUtils";
import {Space} from "antd";
import Table from "@components/common/table/Table";
import CheckIcon from "@components/common/CheckIcon";
import WebhookAuthenticatorSummary from "@components/extension/notifications/webhooks/WebhookAuthenticatorSummary";
import WebhookEditCommand from "@components/extension/notifications/webhooks/WebhookEditCommand";
import WebhookDeleteCommand from "@components/extension/notifications/webhooks/WebhookDeleteCommand";
import {FaExchangeAlt} from "react-icons/fa";
import InlineCommand from "@components/common/InlineCommand";
import WebhookTestCommand from "@components/extension/notifications/webhooks/WebhookTestCommand";

export default function WebhooksView() {

    const [refreshCount, refresh] = useRefresh()
    const {loading, finished, data: webhooks} = useQuery(
        gql`
            query Webhooks {
                webhooks {
                    name
                    enabled
                    url
                    timeoutSeconds
                    authenticationType
                    authenticationConfig
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.webhooks,
            deps: [refreshCount],
        }
    )

    return (
        <>
            <Head>
                {pageTitle("Webhooks")}
            </Head>
            <MainPage
                title="Webhooks"
                breadcrumbs={homeBreadcrumbs()}
                commands={[
                    <WebhookCreateCommand key="new" onSuccess={refresh}/>,
                    <CloseCommand key="home" href={homeUri()}/>,
                ]}
            >
                <Table
                    dataSource={webhooks}
                    pagination={false}
                    loading={loading || !finished}
                >
                    <Table.Column
                        key="name"
                        title="Name"
                        dataIndex="name"
                    />
                    <Table.Column
                        key="enabled"
                        title="Enabled"
                        render={(_, webhook) => <CheckIcon value={webhook.enabled}/>}
                    />
                    <Table.Column
                        key="url"
                        title="URL"
                        dataIndex="url"
                    />
                    <Table.Column
                        key="timeoutSeconds"
                        title="Timeout (s)"
                        dataIndex="timeoutSeconds"
                    />
                    <Table.Column
                        key="authentication"
                        title="Authentication"
                        render={(_, webhook) => <WebhookAuthenticatorSummary
                            authenticationType={webhook.authenticationType}/>}
                    />
                    <Table.Column
                        key="actions"
                        title=""
                        render={(_, webhook) =>
                            <Space>
                                <InlineCommand
                                    icon={<FaExchangeAlt/>}
                                    href={`/extension/notifications/webhook/${webhook.name}`}
                                    title="List of requests to this webhook"
                                />
                                <WebhookTestCommand webhook={webhook}/>
                                <WebhookEditCommand webhook={webhook} onSuccess={refresh}/>
                                <WebhookDeleteCommand webhook={webhook} onSuccess={refresh}/>
                            </Space>
                        }
                    />
                </Table>
            </MainPage>
        </>
    )
}
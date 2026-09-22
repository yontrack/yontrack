import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import MainPage from "@components/layouts/MainPage";
import WebhookListLink, {webhookListUri} from "@components/extension/notifications/webhooks/WebhookLinks";
import {Space, Typography} from "antd";
import WebhookExchangesTable from "@components/extension/notifications/webhooks/WebhookExchangesTable";

export default function WebhookExchangesView({name}) {
    return (
        <>
            <Head>
                {pageTitle(name)}
            </Head>
            <MainPage
                title={name}
                breadcrumbs={[...homeBreadcrumbs(), <WebhookListLink key="webhooks"/>]}
                commands={[
                    <CloseCommand key="webhooks" href={webhookListUri()}/>,
                ]}
            >
                <Space orientation="vertical" className="ot-line">
                    <Typography.Paragraph>
                        List of deliveries for the <strong>{name}</strong> webhook.
                    </Typography.Paragraph>
                    <WebhookExchangesTable name={name}/>
                </Space>
            </MainPage>
        </>
    )
}
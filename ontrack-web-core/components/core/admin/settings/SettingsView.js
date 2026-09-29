import Head from "next/head";
import {title} from "@components/common/Titles";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {homeUri} from "@components/common/Links";
import MainPage from "@components/layouts/MainPage";
import {useState} from "react";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Col, Menu, Row, Typography} from "antd";
import SettingsWrapper from "@components/core/admin/settings/SettingsWrapper";

export default function SettingsView() {

    const {data: settings} = useQuery(
        gql`
            query Settings {
                settings {
                    list {
                        id
                        title
                    }
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.settings.list,
        }
    )

    const [selectedSettingsEntry, setSelectedSettingsEntry] = useState(undefined)

    const items = (settings ?? []).map(entry => ({
        key: entry.id,
        label: <Typography.Text>{entry.title}</Typography.Text>,
        onClick: () => setSelectedSettingsEntry(entry),
    }))

    return (
        <>
            <Head>
                {title("Settings")}
            </Head>
            <MainPage
                title="Settings"
                breadcrumbs={homeBreadcrumbs()}
                commands={[
                    <CloseCommand key="close" href={homeUri()}/>
                ]}
            >
                <Row>
                    <Col span={5}>
                        <Menu
                            mode="inline"
                            items={items}
                        />
                    </Col>
                    <Col span={19}>
                        {
                            selectedSettingsEntry &&
                            <SettingsWrapper
                                entryId={selectedSettingsEntry.id}
                            />
                        }
                    </Col>
                </Row>
            </MainPage>
        </>
    )
}
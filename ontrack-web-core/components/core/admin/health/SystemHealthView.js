import Head from "next/head";
import {title} from "@components/common/Titles";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {homeUri} from "@components/common/Links";
import {Space} from "antd";
import MainPage from "@components/layouts/MainPage";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import LoadingContainer from "@components/common/LoadingContainer";
import SystemHealth from "@components/core/admin/health/SystemHealth";

export default function SystemHealthView() {

    const {data: systemHealth, loading} = useQuery(
        gql`
            query SystemHealth {
                systemHealth {
                    health
                    connectors {
                        statuses {
                            status {
                                description {
                                    connector {
                                        type
                                        name
                                    }
                                    connection
                                }
                                type
                                error
                            }
                            time
                        }
                        count
                        upCount
                        downCount
                        status
                        percent
                    }
                }
            }
        `,
        {
            dataFn: data => data.systemHealth,
        }
    )

    return (
        <>
            <Head>
                {title("System health")}
            </Head>
            <MainPage
                title="System health"
                breadcrumbs={homeBreadcrumbs()}
                commands={[
                    <CloseCommand key="close" href={homeUri()}/>
                ]}
            >
                <Space orientation="vertical" className="ot-line">
                    <LoadingContainer loading={loading}>
                        {
                            systemHealth &&
                            <SystemHealth systemHealth={systemHealth}/>
                        }
                    </LoadingContainer>
                </Space>
            </MainPage>
        </>
    )
}
import CascLocations from "@components/extension/casc/CascLocations";
import {Button, Card, message, Popconfirm, Space, Typography} from "antd";
import {FaSync} from "react-icons/fa";
import {callGraphQL, useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {processGraphQLErrors} from "@components/services/graphql-utils";
import {useState} from "react";
import {useRefresh} from "@components/common/RefreshUtils";
import LoadingContainer from "@components/common/LoadingContainer";
import Yaml from "@components/common/Yaml";

export default function Casc() {

    const [messageApi, contextHolder] = message.useMessage()

    const [reloading, setReloading] = useState(false)
    const reloadCasc = async () => {
        setReloading(true)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation ReloadCasc {
                        reloadCasc {
                            errors {
                                message
                            }
                        }
                    }
                `,
            })

            if (processGraphQLErrors(data, 'reloadCasc', messageApi)) {
                reload()
            }
        } finally {
            setReloading(false)
        }
    }

    const [loadState, reload] = useRefresh()
    // Only loaded on demand: by the "Load" button, or after a reload of the configuration
    const {data, loading} = useQuery(
        gql`
            query Casc {
                casc {
                    yaml
                }
            }
        `,
        {
            deps: [loadState],
            condition: loadState > 0,
            initialData: '',
            dataFn: data => data.casc.yaml,
        }
    )
    const cascYaml = data ?? ''

    return (
        <>
            {contextHolder}
            <Space className="ot-line" orientation="vertical">
                <Card
                    title="Casc locations"
                    extra={
                        <Popconfirm
                            title="Reload configuration as code"
                            description="Are you sure to reload the configuration as code?"
                            onConfirm={reloadCasc}
                        >
                            <Button key="reload" color="danger" variant="filled" loading={reloading}>
                                <Space>
                                    <FaSync/>
                                    <Typography.Text>Reload configuration</Typography.Text>
                                </Space>
                            </Button>
                        </Popconfirm>
                    }
                >
                    <CascLocations/>
                </Card>
                <Card
                    title="Current configuration"
                    extra={
                        <Button key="show" loading={loading} onClick={reload}>
                            <Space>
                                <FaSync/>
                                <Typography.Text>Load</Typography.Text>
                            </Space>
                        </Button>
                    }
                >
                    <LoadingContainer loading={loading}>
                        {
                            cascYaml &&
                            <Yaml yaml={cascYaml}/>
                        }

                    </LoadingContainer>
                </Card>
            </Space>
        </>
    )
}
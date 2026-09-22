import CascLocations from "@components/extension/casc/CascLocations";
import {Button, Card, message, Popconfirm, Space, Typography} from "antd";
import {FaSync} from "react-icons/fa";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {processGraphQLErrors} from "@components/services/graphql-utils";
import {useEffect, useState} from "react";
import {useReloadState} from "@components/common/StateUtils";
import LoadingContainer from "@components/common/LoadingContainer";
import Yaml from "@components/common/Yaml";

export default function Casc() {

    const [messageApi, contextHolder] = message.useMessage()
    const client = useGraphQLClient()

    const [reloading, setReloading] = useState(false)
    const reloadCasc = async () => {
        setReloading(true)
        try {
            const data = await client.request(
                gql`
                    mutation ReloadCasc {
                        reloadCasc {
                            errors {
                                message
                            }
                        }
                    }
                `
            )

            if (processGraphQLErrors(data, 'reloadCasc', messageApi)) {
                reload()
            }
        } finally {
            setReloading(false)
        }
    }

    const [loadState, reload] = useReloadState()
    const [loading, setLoading] = useState(false)
    const [cascYaml, setCascYaml] = useState('')

    useEffect(() => {
        if (client && loadState > 0) {
            setLoading(true)
            client.request(
                gql`
                    query Casc {
                        casc {
                            yaml
                        }
                    }
                `
            ).then(data => {
                setCascYaml(data.casc.yaml)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, loadState])

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
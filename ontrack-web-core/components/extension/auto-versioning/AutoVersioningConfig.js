import {Empty, Space, Table, Typography} from "antd";
import ProjectLinkByName from "@components/projects/ProjectLinkByName";
import AutoVersioningApproval from "@components/extension/auto-versioning/AutoVersioningApproval";
import AutoVersioningConfigDetails from "@components/extension/auto-versioning/AutoVersioningConfigDetails";
import {isAuthorized} from "@components/common/authorizations";
import InlineConfirmCommand from "@components/common/InlineConfirmCommand";
import {useEffect, useState} from "react";
import FieldLabel from "@components/extension/auto-versioning/AutoVersioningFieldLabel";

const {Column} = Table

export default function AutoVersioningConfig({branch, config, onDeleteConfig}) {

    const [items, setItems] = useState([])
    useEffect(() => {
        setItems(config.configurations.map((config, index) => ({
            ...config,
            key: index,
        })))
    }, [config])

    return (
        <>
            {
                !config && <Empty description="No auto-versioning configuration."/>
            }
            {
                config && <Table
                    data-testid="auto-versioning-config-table"
                    dataSource={items}
                    pagination={false}
                    expandable={{
                        expandedRowRender: (source) => (
                            <>
                                <AutoVersioningConfigDetails source={source}/>
                            </>
                        )
                    }}
                >

                    <Column
                        key="sourceProject"
                        title={<FieldLabel fieldName="sourceProject" description="Project to watch"/>}
                        render={(_, source) => (
                            <>
                                <ProjectLinkByName name={source.sourceProject}/>
                            </>
                        )}
                    />

                    <Column
                        key="sourceBranch"
                        title={<FieldLabel fieldName="sourceBranch"
                                           description="Branches to watch using a regular expression"/>}
                        render={(_, source) => (
                            <>
                                <Typography.Text code>{source.sourceBranch}</Typography.Text>
                            </>
                        )}
                    />

                    <Column
                        key="sourcePromotion"
                        title={<FieldLabel fieldName="sourcePromotion" description="Promotion to watch"/>}
                        render={(_, source) => (
                            <>
                                <Typography.Text code>{source.sourcePromotion}</Typography.Text>
                            </>
                        )}
                    />

                    <Column
                        key="approval"
                        title={<FieldLabel fieldName="autoApproval / autoApprovalMode"
                                           description="Auto-approval and approval mode"/>}
                        render={(_, source) =>
                            <>
                                <AutoVersioningApproval
                                    autoApproval={source.autoApproval}
                                    autoApprovalMode={source.autoApprovalMode}
                                />
                            </>
                        }
                    />

                    <Column
                        key="targetPath"
                        title={<FieldLabel fieldName="targetPath"
                                           description="Comma-separated list of files to update with the new version"/>}
                        render={(_, source) =>
                            <>
                                <Typography.Text code>{source.targetPath}</Typography.Text>
                            </>
                        }
                    />

                    {
                        isAuthorized(branch, 'branch', 'config') &&
                        <Column
                            key="actions"
                            title=""
                            render={(_, __, index) => (
                                <Space>
                                    <InlineConfirmCommand
                                        title="Are you sure to delete this configuration?"
                                        onConfirm={() => onDeleteConfig(index)}
                                    />
                                </Space>
                            )}
                        />
                    }

                </Table>
            }
        </>
    )
}
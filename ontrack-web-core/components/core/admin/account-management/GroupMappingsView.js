import Head from "next/head";
import {title} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import AccountManagementLink, {
    accountManagementUri
} from "@components/core/admin/account-management/AccountManagementLink";
import {CloseCommand} from "@components/common/Commands";
import {Button, Form, Input, Space, Table, Typography} from "antd";
import {useRefresh} from "@components/common/RefreshUtils";
import {
    useGroupMappings,
    useMutationAddGroupMapping
} from "@components/core/admin/account-management/GroupMappingsService";
import TableFormSection from "@components/common/table/TableFormSection";
import {FaPlus} from "react-icons/fa";
import SelectMultipleAccountGroups from "@components/core/admin/account-management/SelectMultipleAccountGroups";
import DeleteGroupMappingCommand from "@components/core/admin/account-management/DeleteGroupMappingCommand";

export default function GroupMappingsView() {

    const [refreshState, refresh] = useRefresh()
    const {groupMappings, loading} = useGroupMappings({refreshState})

    const onCreated = () => {
        form.resetFields()
        refresh()
    }

    const {addGroupMapping} = useMutationAddGroupMapping({onSuccess: onCreated})

    const [form] = Form.useForm()

    const onCreate = async (values) => {
        await addGroupMapping(values)
    }

    return (
        <>
            <Head>
                {title("Group mappings")}
            </Head>
            <MainPage
                title="Group mappings"
                breadcrumbs={[
                    ...homeBreadcrumbs(),
                    <AccountManagementLink key="account-management"/>,
                ]}
                commands={[
                    <CloseCommand key="close" href={accountManagementUri}/>,
                ]}
            >
                <Space orientation="vertical" className="ot-line">
                    <Typography.Paragraph italic type="secondary">
                        This page allows you to map groups sent by the identity provider
                        in its JWT access token to groups in Yontrack.
                    </Typography.Paragraph>
                    <TableFormSection>
                        <Form
                            form={form}
                            layout="inline"
                            onFinish={onCreate}
                        >
                            <Form.Item
                                name="idpGroup"
                                rules={[{required: true, message: 'IdP group is required'}]}
                            >
                                <Input/>
                            </Form.Item>
                            <Form.Item
                                name="groupId"
                                rules={[{required: true, message: 'Group is required'}]}
                            >
                                <SelectMultipleAccountGroups mode=""/>
                            </Form.Item>
                            <Button
                                type="primary"
                                htmlType="submit"
                                loading={loading}
                            >
                                <Space>
                                    <FaPlus/>
                                    Add mapping
                                </Space>
                            </Button>
                        </Form>
                    </TableFormSection>
                    <Table
                        loading={loading}
                        dataSource={groupMappings}
                        pagination={false}
                    >
                        <Table.Column
                            key="idpGroup"
                            title="IdP group"
                            dataIndex="idpGroup"
                        />
                        <Table.Column
                            key="group"
                            title="Yontrack group"
                            render={(_, groupMapping) => groupMapping.group.name}
                        />
                        <Table.Column
                            key="group"
                            title=""
                            render={(_, groupMapping) =>
                                <Space>
                                    <DeleteGroupMappingCommand groupMapping={groupMapping} refresh={refresh}/>
                                </Space>
                            }
                        />
                    </Table>
                </Space>
            </MainPage>
        </>
    )
}
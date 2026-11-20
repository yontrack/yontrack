import Head from "next/head";
import {title} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import AccountManagementLink, {
    accountManagementUri
} from "@components/core/admin/account-management/AccountManagementLink";
import {CloseCommand} from "@components/common/Commands";
import {Button, Form, Space} from "antd";
import Table from "@components/common/table/Table";
import {useRefresh} from "@components/common/RefreshUtils";
import {
    groupGlobalPermissionsByTarget,
    useGlobalPermissions,
    useMutationAddGlobalPermissionToAccount,
    useMutationAddGlobalPermissionToAccountGroup
} from "@components/core/admin/account-management/GlobalPermissionsService";
import GlobalRole from "@components/core/admin/account-management/GlobalRole";
import PermissionTarget from "@components/core/admin/account-management/PermissionTarget";
import TableFormSection from "@components/common/table/TableFormSection";
import {FaPlus} from "react-icons/fa";
import SelectGlobalRole from "@components/core/admin/account-management/SelectGlobalRole";
import SelectPermissionTarget from "@components/core/admin/account-management/SelectPermissionTarget";
import RevokeGlobalRoleCommand from "@components/core/admin/account-management/RevokeGlobalRoleCommand";

export default function GlobalPermissionsView() {

    const [refreshState, refresh] = useRefresh()
    const {globalPermissions, loading} = useGlobalPermissions({refreshState})
    // One row per account or group, with all its global roles
    const rows = groupGlobalPermissionsByTarget(globalPermissions)

    const [form] = Form.useForm()

    const {
        addGlobalPermissionToAccount,
        loading: loadingAccount
    } = useMutationAddGlobalPermissionToAccount({onSuccess: refresh})
    const {
        addGlobalPermissionToAccountGroup,
        loading: loadingAccountGroup
    } = useMutationAddGlobalPermissionToAccountGroup({onSuccess: refresh})

    const onCreate = async (values) => {
        const [type, id] = values.target.split("-")
        const role = values.role
        if (type === 'ACCOUNT') {
            await addGlobalPermissionToAccount({accountId: id, role})
            form.resetFields()
        } else if (type === 'GROUP') {
            await addGlobalPermissionToAccountGroup({accountGroupId: id, role})
            form.resetFields()
        }
    }

    return (
        <>
            <Head>
                {title("Global permissions")}
            </Head>
            <MainPage
                title="Global permissions"
                breadcrumbs={[
                    ...homeBreadcrumbs(),
                    <AccountManagementLink key="account-management"/>,
                ]}
                commands={[
                    <CloseCommand key="close" href={accountManagementUri}/>,
                ]}
            >
                <Space orientation="vertical" className="ot-line">
                    <TableFormSection>
                        <Form
                            form={form}
                            layout="inline"
                            onFinish={onCreate}
                        >
                            <Form.Item
                                name="target"
                                rules={[{required: true, message: 'Target is required'}]}
                            >
                                <SelectPermissionTarget/>
                            </Form.Item>
                            <Form.Item
                                name="role"
                                rules={[{required: true, message: 'Role is required'}]}
                            >
                                <SelectGlobalRole/>
                            </Form.Item>
                            <Button
                                type="primary"
                                htmlType="submit"
                                loading={loadingAccount || loadingAccountGroup}
                            >
                                <Space>
                                    <FaPlus/>
                                    Add permission
                                </Space>
                            </Button>
                        </Form>
                    </TableFormSection>
                    <Table
                        dataSource={rows}
                        rowKey="key"
                        loading={loading}
                        pagination={false}
                    >
                        <Table.Column
                            key="name"
                            title="Name"
                            render={(_, row) => <PermissionTarget target={row.target}/>}
                        />
                        <Table.Column
                            key="roles"
                            title="Roles"
                            render={(_, row) =>
                                <Space wrap data-testid={`global-roles-${row.key}`}>
                                    {
                                        row.roles.map(role =>
                                            <Space
                                                key={role.id}
                                                size={0}
                                                data-testid={`global-role-${row.key}-${role.id}`}
                                            >
                                                <GlobalRole role={role}/>
                                                <RevokeGlobalRoleCommand
                                                    target={row.target}
                                                    role={role}
                                                    refresh={refresh}
                                                />
                                            </Space>
                                        )
                                    }
                                </Space>
                            }
                        />
                    </Table>
                </Space>
            </MainPage>
        </>
    )
}
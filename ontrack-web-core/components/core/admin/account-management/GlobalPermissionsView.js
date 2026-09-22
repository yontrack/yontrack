import Head from "next/head";
import {title} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import AccountManagementLink, {
    accountManagementUri
} from "@components/core/admin/account-management/AccountManagementLink";
import {CloseCommand} from "@components/common/Commands";
import {Button, Form, Space, Table} from "antd";
import {useRefresh} from "@components/common/RefreshUtils";
import {
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
import DeleteGlobalPermissionCommand from "@components/core/admin/account-management/DeleteGlobalPermissionCommand";

export default function GlobalPermissionsView() {

    const [refreshState, refresh] = useRefresh()
    const {globalPermissions, loading} = useGlobalPermissions({refreshState})

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
                        dataSource={globalPermissions}
                        loading={loading}
                        pagination={false}
                    >
                        <Table.Column
                            key="name"
                            title="Name"
                            render={(_, globalPermission) => <PermissionTarget target={globalPermission.target}/>}
                        />
                        <Table.Column
                            key="role"
                            title="Role"
                            render={(_, globalPermission) => <GlobalRole role={globalPermission.role}/>}
                        />
                        <Table.Column
                            key="actions"
                            title="Actions"
                            render={(_, globalPermission) =>
                                    <DeleteGlobalPermissionCommand globalPermission={globalPermission} refresh={refresh}/>
                            }
                        />
                    </Table>
                </Space>
            </MainPage>
        </>
    )
}
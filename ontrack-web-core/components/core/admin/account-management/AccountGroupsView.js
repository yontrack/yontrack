import Head from "next/head";
import {title} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {Button, Form, Input, Space, Table} from "antd";
import AccountManagementLink, {
    accountManagementUri
} from "@components/core/admin/account-management/AccountManagementLink";
import {useRefresh} from "@components/common/RefreshUtils";
import {useState} from "react";
import {useAccountGroups} from "@components/core/admin/account-management/AccountManagementService";
import {FaSearch} from "react-icons/fa";
import CreateAccountGroupCommand from "@components/core/admin/account-management/CreateAccountGroupCommand";
import EditAccountGroupCommand from "@components/core/admin/account-management/EditAccountGroupCommand";
import DeleteAccountGroupCommand from "@components/core/admin/account-management/DeleteAccountGroupCommand";

export default function AccountGroupsView() {

    const [refreshState, refresh] = useRefresh()
    const [name, setName] = useState('')
    const {groups, loading} = useAccountGroups({refreshState, name})

    const onSearch = (values) => {
        const name = values.name
        setName(name)
    }

    return (
        <>
            <Head>
                {title("Account groups")}
            </Head>
            <MainPage
                title="Account groups"
                breadcrumbs={[
                    ...homeBreadcrumbs(),
                    <AccountManagementLink key="account-management"/>,
                ]}
                commands={[
                    <CreateAccountGroupCommand key="create" refresh={refresh}/>,
                    <CloseCommand key="close" href={accountManagementUri}/>,
                ]}
            >
                <Space orientation="vertical" className="ot-line">
                    <Form layout="inline" onFinish={onSearch}>
                        <Form.Item
                            key="name"
                            label="Search"
                            name="name"
                        >
                            <Input
                                placeholder="Name, description..."
                                style={{width: '30em'}}
                                allowClear={true}
                                onClear={() => setName('')}
                            />
                        </Form.Item>
                        <Form.Item>
                            <Button htmlType="submit" icon={<FaSearch/>}></Button>
                        </Form.Item>
                    </Form>
                    <Table
                        dataSource={groups}
                        loading={loading}
                    >
                        <Table.Column
                            key="name"
                            title="Name"
                            dataIndex="name"
                        />
                        <Table.Column
                            key="description"
                            title="Description"
                            dataIndex="description"
                        />
                        <Table.Column
                            key="actions"
                            title="Actions"
                            render={(_, accountGroup) =>
                                <Space>
                                    <EditAccountGroupCommand accountGroup={accountGroup} refresh={refresh}/>
                                    <DeleteAccountGroupCommand accountGroup={accountGroup} refresh={refresh}/>
                                </Space>
                            }
                        />
                    </Table>
                </Space>
            </MainPage>
        </>
    )
}
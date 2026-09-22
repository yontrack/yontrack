import Head from "next/head";
import {title} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {homeUri} from "@components/common/Links";
import {useAccounts} from "@components/core/admin/account-management/AccountManagementService";
import {useRefresh} from "@components/common/RefreshUtils";
import {Button, Form, Input, Space, Table, Tag, Typography} from "antd";
import {FaSearch} from "react-icons/fa";
import {useState} from "react";
import AccountGroupsCommand from "@components/core/admin/account-management/AccountGroupsCommand";
import AccountTokens from "@components/core/admin/account-management/AccountTokens";
import EditAccountCommand from "@components/core/admin/account-management/EditAccountCommand";
import DeleteAccountCommand from "@components/core/admin/account-management/DeleteAccountCommand";
import RevokeAllTokensCommand from "@components/core/admin/account-management/RevokeAllTokensCommand";
import GlobalPermissionsCommand from "@components/core/admin/account-management/GlobalPermissionsCommand";
import GroupMappingsCommand from "@components/core/admin/account-management/GroupMappingsCommand";

export default function AccountManagementView() {

    const [refreshState, refresh] = useRefresh()
    const [token, setToken] = useState('')
    const {accounts, loading} = useAccounts({refreshState, token})

    const onSearch = (values) => {
        const token = values.token
        setToken(token)
    }

    return (
        <>
            <Head>
                {title("Account management")}
            </Head>
            <MainPage
                title="Account management"
                breadcrumbs={homeBreadcrumbs()}
                commands={[
                    <GlobalPermissionsCommand key="permissions"/>,
                    <AccountGroupsCommand key="groups"/>,
                    <GroupMappingsCommand key="mappings"/>,
                    <RevokeAllTokensCommand key="revoke" refresh={refresh}/>,
                    <CloseCommand key="close" href={homeUri()}/>,
                ]}
            >
                <Space orientation="vertical" className="ot-line">
                    <Form layout="inline" onFinish={onSearch}>
                        <Form.Item
                            key="search"
                            label="Search"
                            name="token"
                        >
                            <Input
                                placeholder="Name, email, group..."
                                style={{width: '30em'}}
                                allowClear={true}
                                onClear={() => setToken('')}
                            />
                        </Form.Item>
                        <Form.Item>
                            <Button htmlType="submit" icon={<FaSearch/>}></Button>
                        </Form.Item>
                    </Form>
                    <Table
                        dataSource={accounts}
                        loading={loading}
                    >
                        <Table.Column
                            key="email"
                            title="Email"
                            dataIndex="email"
                        />
                        <Table.Column
                            key="fullName"
                            title="Full name"
                            dataIndex="fullName"
                        />
                        <Table.Column
                            key="groups"
                            title="Groups"
                            render={(_, account) => <>
                                {
                                    account.groups.length === 0 &&
                                    <Typography.Text type="secondary">No group</Typography.Text>
                                }
                                {
                                    account.groups.length > 0 &&
                                    <Space>
                                        {
                                            account.groups.map(group =>
                                                <Tag key={group.id}>{group.name}</Tag>
                                            )
                                        }
                                    </Space>
                                }
                            </>}
                        />
                        <Table.Column
                            key="tokens"
                            title="Tokens"
                            render={(_, account) =>
                                <AccountTokens
                                    accountId={account.id}
                                    tokens={account.tokens}
                                    refresh={refresh}
                                />
                            }
                        />
                        <Table.Column
                            key="actions"
                            title="Actions"
                            render={(_, account) =>
                                <Space>
                                    <EditAccountCommand account={account} refresh={refresh}/>
                                    <DeleteAccountCommand account={account} refresh={refresh}/>
                                </Space>
                            }
                        />
                    </Table>
                </Space>
            </MainPage>
        </>
    )
}
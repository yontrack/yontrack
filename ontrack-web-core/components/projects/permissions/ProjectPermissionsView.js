import {useState} from "react";
import Head from "next/head";
import {Alert, Button, Empty, Form, Space} from "antd";
import {FaPlus} from "react-icons/fa";
import MainPage from "@components/layouts/MainPage";
import {projectTitle} from "@components/common/Titles";
import {downToProjectBreadcrumbs, homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {projectUri} from "@components/common/Links";
import LoadingContainer from "@components/common/LoadingContainer";
import Table from "@components/common/table/Table";
import TableFormSection from "@components/common/table/TableFormSection";
import {useRefresh} from "@components/common/RefreshUtils";
import {isAuthorized} from "@components/common/authorizations";
import PermissionTarget from "@components/core/admin/account-management/PermissionTarget";
import SelectPermissionTarget from "@components/core/admin/account-management/SelectPermissionTarget";
import {
    grantProjectRoles,
    groupProjectPermissionsByTarget,
    parsePermissionTarget,
    projectRolesOf,
    useProjectPermissions
} from "@components/projects/permissions/ProjectPermissionsService";
import ProjectRoleTag from "@components/projects/permissions/ProjectRoleTag";
import RevokeProjectRoleCommand from "@components/projects/permissions/RevokeProjectRoleCommand";
import SelectProjectRoles from "@components/projects/permissions/SelectProjectRoles";

/**
 * The permissions of a project: one row per account or group, with its roles on the project,
 * each revoked on its own; and a form granting one or more roles to an account or a group (#2164).
 */
export default function ProjectPermissionsView({id}) {

    const [refreshState, refresh] = useRefresh()
    const {project, loading, error: loadingError} = useProjectPermissions({id, refreshState})

    const rows = groupProjectPermissionsByTarget(project?.projectRoles)
    const roles = projectRolesOf(project?.projectRoles)
    const authorized = project ? isAuthorized(project, 'project', 'permissions') : false

    const [form] = Form.useForm()
    const [granting, setGranting] = useState(false)
    const [error, setError] = useState('')

    const onGrant = async (values) => {
        setError('')
        setGranting(true)
        try {
            await grantProjectRoles({
                projectId: project.id,
                target: parsePermissionTarget(values.target),
                projectRoles: values.roles,
            })
            form.resetFields()
        } catch (ex) {
            setError(ex.message)
        } finally {
            setGranting(false)
            refresh()
        }
    }

    const onRevoked = () => {
        setError('')
        refresh()
    }

    return (
        <>
            <Head>
                {projectTitle(project, "Permissions")}
            </Head>
            <MainPage
                title="Permissions"
                breadcrumbs={project ? downToProjectBreadcrumbs({project}) : homeBreadcrumbs()}
                commands={[
                    <CloseCommand key="close" href={projectUri({id})}/>,
                ]}
            >
                <LoadingContainer loading={loading && !project}>
                    {
                        loadingError &&
                        <Alert
                            type="error"
                            showIcon
                            title={`The permissions of this project cannot be read: ${loadingError}`}
                        />
                    }
                    {
                        !project && !loadingError &&
                        <Empty description="This project does not exist, or you are not allowed to see it."/>
                    }
                    {
                        project && !authorized &&
                        <Alert
                            type="warning"
                            showIcon
                            title="You are not allowed to manage the permissions of this project."
                        />
                    }
                    {
                        project && authorized &&
                        <Space orientation="vertical" className="ot-line">
                            {
                                error &&
                                <Alert
                                    type="error"
                                    showIcon
                                    closable
                                    onClose={() => setError('')}
                                    title={error}
                                />
                            }
                            <TableFormSection>
                                <Form
                                    form={form}
                                    layout="inline"
                                    onFinish={onGrant}
                                >
                                    <Form.Item
                                        name="target"
                                        rules={[{required: true, message: 'Account or group is required'}]}
                                    >
                                        <SelectPermissionTarget projectId={project.id}/>
                                    </Form.Item>
                                    <Form.Item
                                        name="roles"
                                        rules={[{required: true, message: 'At least one role is required'}]}
                                    >
                                        <SelectProjectRoles roles={roles}/>
                                    </Form.Item>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={granting}
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
                                locale={{emptyText: "No account or group holds a role on this project."}}
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
                                        <Space wrap data-testid={`project-roles-${row.key}`}>
                                            {
                                                row.roles.map(role =>
                                                    <Space
                                                        key={role.id}
                                                        size={0}
                                                        data-testid={`project-role-${row.key}-${role.id}`}
                                                    >
                                                        <ProjectRoleTag role={role}/>
                                                        <RevokeProjectRoleCommand
                                                            projectId={project.id}
                                                            target={row.target}
                                                            role={role}
                                                            onSuccess={onRevoked}
                                                            onError={setError}
                                                        />
                                                    </Space>
                                                )
                                            }
                                        </Space>
                                    }
                                />
                            </Table>
                        </Space>
                    }
                </LoadingContainer>
            </MainPage>
        </>
    )
}

import {useContext, useEffect, useState} from "react";
import {UserContext} from "@components/providers/UserProvider";
import {
    FaCheck,
    FaCode,
    FaCopy,
    FaEdit,
    FaLock,
    FaPlus,
    FaRegCopy,
    FaTrash,
    FaUpload,
    FaUserLock,
    FaUsers,
    FaWindowRestore
} from "react-icons/fa";
import {Button, Dropdown, message, Space, Typography} from "antd";
import {DashboardContext} from "@components/dashboards/DashboardContextProvider";
import SaveDashboardDialog, {useSaveDashboardDialog} from "@components/dashboards/SaveDashboardDialog";
import DashboardYamlExportDialog, {useDashboardYamlExportDialog} from "@components/dashboards/DashboardYamlExportDialog";
import DashboardYamlImportDialog, {useDashboardYamlImportDialog} from "@components/dashboards/DashboardYamlImportDialog";
import {useRouter} from "next/router";
import copy from "copy-to-clipboard";
import Link from "next/link";

export default function DashboardCommandMenu() {

    const user = useContext(UserContext)

    const router = useRouter()
    const context = useContext(DashboardContext)

    const saveDashboardDialog = useSaveDashboardDialog({
        onSuccess: (_, {__, edition}) => {
            context.refresh()
            if (edition) {
                context.startEdition()
            }
        }
    })

    const yamlExportDialog = useDashboardYamlExportDialog()

    const yamlImportDialog = useDashboardYamlImportDialog({
        onSuccess: () => context.refresh(),
    })

    const copyDashboard = () => {
        const selectedDashboard = context?.dashboard
        if (selectedDashboard) {
            const link = `${window.location.origin}${router.pathname}?dashboard=${selectedDashboard.uuid}`
            if (copy(link)) {
                message.success(
                    <Space direction="vertical">
                        <Typography.Paragraph>Dashboard URL copied</Typography.Paragraph>
                        <Typography.Paragraph>
                            <Link href={link}>{link}</Link>
                        </Typography.Paragraph>
                    </Space>
                )
            }
        }
    }

    const editDashboard = () => {
        context.startEdition()
    }

    const cloneDashboard = () => {
        const selectedDashboard = context?.dashboard
        if (selectedDashboard) {
            const copy = {
                ...selectedDashboard,
                uuid: '',
                name: `Copy of ${selectedDashboard.name}`,
                userScope: 'PRIVATE',
                widgets: selectedDashboard.widgets.map(widget => ({
                    ...widget,
                    uuid: ''
                }))
            }
            // Opening the dialog
            saveDashboardDialog.start({copy})
        }
    }

    const createDashboard = () => {
        const copy = {
            uuid: '',
            name: `New dashboard`,
            userScope: 'PRIVATE',
            widgets: [],
        }
        // Opening the dialog
        saveDashboardDialog.start({copy, edition: true})
    }

    const [items, setItems] = useState([])

    useEffect(() => {
        const menu = []

        // All dashboards
        context?.dashboards.forEach(dashboard => {

            // Type of dashboards
            let icon = undefined
            switch (dashboard.userScope) {
                case 'PRIVATE':
                    icon = <FaUserLock title="This dashboard is visible only to you."/>
                    break
                case 'SHARED':
                    icon = <FaUsers title="This dashboard is shared for all users."/>
                    break
                case 'BUILT_IN':
                    icon = <FaLock title="This dashboard built-in and cannot be edited."/>
                    break
            }

            // Selection indicator
            let text = <Typography.Text>{dashboard.name}</Typography.Text>
            if (context?.dashboard && context?.dashboard?.uuid === dashboard.uuid) {
                text = <Space size={8}>
                    <FaCheck/>
                    <Typography.Text strong>{dashboard.name}</Typography.Text>
                </Space>
            } else {
                text = <Typography.Text>{dashboard.name}</Typography.Text>
            }

            // Menu entry
            menu.push({
                key: dashboard.uuid,
                icon: icon,
                label: text,
                onClick: () => context.selectDashboard(dashboard),
            })
        })

        // Separator
        menu.push({type: 'divider'})

        // Copying URL to current dashboard
        if (context?.dashboard) {
            menu.push({
                key: 'copy',
                icon: <FaRegCopy/>,
                label: "Copy dashboard URL",
                onClick: copyDashboard,
            })
        }

        // Exporting as YAML
        if (context?.dashboard && context.dashboard.userScope !== 'BUILT_IN') {
            menu.push({
                key: 'exportYaml',
                icon: <FaCode/>,
                label: "Export as YAML",
                onClick: () => yamlExportDialog.start(context.dashboard),
            })
        }

        // Importing from YAML
        menu.push({
            key: 'importYaml',
            icon: <FaUpload/>,
            label: "Import dashboards as YAML",
            onClick: () => yamlImportDialog.start({}),
        })

        // Editing current
        if (context?.dashboard && context.dashboard.authorizations.edit) {
            menu.push({
                key: 'edit',
                icon: <FaEdit/>,
                label: "Edit current dashboard",
                onClick: editDashboard,
            })
        }

        // Sharing current
        if (context?.dashboard && context.dashboard.userScope === 'PRIVATE' && context.dashboard.authorizations?.share) {
            menu.push({
                key: 'share',
                icon: <FaUsers/>,
                label: "Share current dashboard",
                onClick: context.shareDashboard,
            })
        }

        // Cloning current
        if (user?.authorizations?.dashboard?.edit) {
            menu.push({
                key: 'clone',
                icon: <FaCopy/>,
                label: "Clone current dashboard",
                onClick: cloneDashboard,
            })
        }

        // Deleting current
        if (context?.dashboard && context.dashboard.authorizations?.delete) {
            menu.push({
                key: 'delete',
                icon: <FaTrash/>,
                label: "Delete current dashboard",
                danger: true,
                onClick: context.deleteDashboard,
            })
        }

        // New dashboard
        if (user?.authorizations?.dashboard?.edit) {
            menu.push({type: 'divider'})
            menu.push({
                key: 'new',
                icon: <FaPlus/>,
                label: "Create a new dashboard",
                onClick: createDashboard,
            })
        }

        // OK
        setItems(menu)

    }, [context.dashboards?.length, context?.dashboard, user, yamlExportDialog, yamlImportDialog]);

    return (
        <>
            <Dropdown menu={{items}}>
                <Button
                    type="text"
                >
                    <Space size={8}>
                        <FaWindowRestore/>
                        <Typography.Text>Dashboard</Typography.Text>
                    </Space>
                </Button>
            </Dropdown>
            <SaveDashboardDialog saveDashboardDialog={saveDashboardDialog}/>
            <DashboardYamlExportDialog dialog={yamlExportDialog}/>
            <DashboardYamlImportDialog dialog={yamlImportDialog}/>
        </>
    )
}
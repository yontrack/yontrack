import {useState} from "react";
import {Button, message, Modal, Space, Typography} from "antd";
import {FaCopy} from "react-icons/fa";
import copy from "copy-to-clipboard";
import Yaml from "@components/common/Yaml";

export function useDashboardYamlExportDialog() {
    const [open, setOpen] = useState(false)
    const [yaml, setYaml] = useState('')

    const start = (dashboard) => {
        if (dashboard.userScope === 'BUILT_IN') return
        setYaml(dashboard.asYaml ?? '')
        setOpen(true)
    }

    return {open, setOpen, yaml, start}
}

export default function DashboardYamlExportDialog({dialog}) {
    const [messageApi, contextHolder] = message.useMessage()

    const copyToClipboard = () => {
        if (copy(dialog.yaml)) {
            messageApi.success("YAML copied to clipboard")
        }
    }

    return (
        <>
            {contextHolder}
            <Modal
                title="Export dashboard as YAML"
                open={dialog.open}
                onCancel={() => dialog.setOpen(false)}
                footer={
                    <Space>
                        <Button icon={<FaCopy/>} onClick={copyToClipboard}>
                            Copy to clipboard
                        </Button>
                        <Button onClick={() => dialog.setOpen(false)}>Close</Button>
                    </Space>
                }
                width={800}
            >
                <Space direction="vertical" style={{width: '100%'}}>
                    <Typography.Text type="secondary">
                        Paste this YAML into your <Typography.Text code>dashboards.yml</Typography.Text> file
                        and call the <Typography.Text code>applyDashboards</Typography.Text> GraphQL mutation
                        from your CI pipeline.
                    </Typography.Text>
                    <Yaml yaml={dialog.yaml} height="24em"/>
                </Space>
            </Modal>
        </>
    )
}

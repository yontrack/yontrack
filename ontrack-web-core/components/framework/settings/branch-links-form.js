import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Alert, Form, InputNumber} from "antd";

export default function BranchLinksForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item>
                    <Alert
                        type="warning"
                        title="Settings valid only for legacy (V4) branch graph. Will be removed in V5."
                    />
                </Form.Item>
                <Form.Item
                    name="depth"
                    label="Dependency depth"
                    extra="Dependency depth to take into account when computing the branch links graph"
                >
                    <InputNumber
                        min={1}
                        max={10}
                    />
                </Form.Item>
                <Form.Item
                    name="history"
                    label="History"
                    extra="Build history to take into account when computing the branch links graph"
                >
                    <InputNumber
                        min={1}
                        max={20}
                    />
                </Form.Item>
                <Form.Item
                    name="maxLinksPerLevel"
                    label="Max links"
                    extra="Maximum number of links to follow per build"
                >
                    <InputNumber
                        min={1}
                        max={20}
                    />
                </Form.Item>
            </SettingsForm>
        </>
    )
}

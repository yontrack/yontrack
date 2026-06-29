import {Button, Form, Input, Space, Switch, Typography} from "antd";
import {FaPlus, FaTrash} from "react-icons/fa";
import {prefixedFormName} from "@components/form/formUtils";
import DurationPicker from "@components/common/DurationPicker";

export default function YontrackPromotionNotificationChannelForm({prefix}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'project')}
                label="Project"
                extra="[template] Name of the project to validate. If not provided, looks for the event's project if available."
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'branch')}
                label="Branch"
                extra="[template] Name of the branch to validate. If not provided, looks for the event's branch if available."
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'build')}
                label="Build"
                extra="[template] Name of the build to validate. If not provided, looks for the event's build if available."
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'promotion')}
                label="Promotion"
                extra="Name of the promotion level to use."
                rules={[{required: true, message: 'Promotion level is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.List
                name={prefixedFormName(prefix, 'fields')}
                label="Fields"
                extra="Fields to set on the promotion run."
            >
                {(fields, {add, remove}) => (
                    <>
                        <div
                            style={{
                                display: 'flex',
                                rowGap: 16,
                                flexDirection: 'column',
                            }}
                        >
                            {fields.map(({key, name, ...restField}) => (
                                <>
                                    <Space key={key}>
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'name']}
                                            rules={[
                                                {
                                                    required: true,
                                                    message: 'Field name is required.',
                                                },
                                            ]}
                                        >
                                            <Input placeholder="Name"/>
                                        </Form.Item>
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'value']}
                                            rules={[
                                                {
                                                    required: true,
                                                    message: 'Field value is required.',
                                                },
                                            ]}
                                        >
                                            <Input placeholder="[template] Value"/>
                                        </Form.Item>
                                        <FaTrash
                                            onClick={() => {
                                                remove(name)
                                            }}
                                        />
                                    </Space>
                                </>
                            ))}
                            <Button type="dashed" onClick={() => add()} block>
                                <Space>
                                    <FaPlus/>
                                    <Typography.Text>Add field</Typography.Text>
                                </Space>
                            </Button>
                        </div>
                    </>
                )}
            </Form.List>
            <Form.Item
                name={prefixedFormName(prefix, 'waitForPromotion')}
                label="Wait for promotion"
                extra="Waiting for the promotion level associated notifications to be completed"
            >
                <Switch/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'waitForPromotionTimeout')}
                label="Wait for promotion timeout"
                extra="Timeout when waiting for the promotion level associated notifications to be completed"
            >
                <DurationPicker inMilliseconds={false} maxUnit="hour"/>
            </Form.Item>
        </>
    )
}
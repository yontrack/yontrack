import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Button, Form, Input, Select, Space, Switch} from "antd";
import {FaPlus, FaTrash} from "react-icons/fa";

const patternTypes = [
    {value: 'CO_AUTHOR_EMAIL', label: 'Co-author email (regex)'},
    {value: 'TRAILER', label: 'Trailer key'},
    {value: 'AUTHOR_EMAIL', label: 'Author email (regex)'},
    {value: 'LOGIN', label: 'Login'},
]

export default function AgentMarkersForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="builtInConventions"
                    label="Built-in conventions"
                    valuePropName="checked"
                    extra="Recognises the Co-Authored-By trailers of Claude Code, Codex and Copilot, the Assisted-by and Claude-Session trailers, and the Copilot and Devin bot accounts."
                >
                    <Switch/>
                </Form.Item>
                <Form.Item
                    label="Patterns"
                    extra="Patterns add to the built-in conventions. A regular expression matches the whole email and ignores the case; a trailer key ignores the case; a login is the exact login or name of the author or committer."
                >
                    <Form.List name="patterns">
                        {(fields, {add, remove}) => (
                            <Space orientation="vertical" style={{width: '100%'}}>
                                {fields.map(({key, name, ...restField}) => (
                                    <Space key={key} align="baseline" wrap>
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'name']}
                                            rules={[{required: true, message: 'The name of the assistant is required.'}]}
                                        >
                                            <Input placeholder="Assistant" aria-label="Assistant"/>
                                        </Form.Item>
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'type']}
                                            rules={[{required: true, message: 'The type is required.'}]}
                                        >
                                            <Select
                                                placeholder="Type"
                                                aria-label="Type"
                                                options={patternTypes}
                                                style={{minWidth: '14em'}}
                                            />
                                        </Form.Item>
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'value']}
                                            rules={[{required: true, message: 'The value is required.'}]}
                                        >
                                            <Input placeholder="Value" aria-label="Value"/>
                                        </Form.Item>
                                        <Button
                                            icon={<FaTrash/>}
                                            aria-label="Remove the pattern"
                                            title="Remove the pattern"
                                            onClick={() => remove(name)}
                                        />
                                    </Space>
                                ))}
                                <Button
                                    type="dashed"
                                    icon={<FaPlus/>}
                                    onClick={() => add({type: 'CO_AUTHOR_EMAIL'})}
                                >
                                    Add a pattern
                                </Button>
                            </Space>
                        )}
                    </Form.List>
                </Form.Item>
            </SettingsForm>
        </>
    )
}

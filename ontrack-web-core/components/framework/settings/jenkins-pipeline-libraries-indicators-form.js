import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Button, Col, Form, Input, Row, Space, Switch} from "antd";
import {FaPlus, FaTrash} from "react-icons/fa";

export default function JenkinsPipelineLibrariesIndicatorsForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.List name="libraryVersions">
                    {(fields, {add, remove}) => (
                        <>
                            {fields.map(({key, name, ...restField}) => (
                                <Space orientation="vertical" key={key} className="ot-form-list-item ot-line">
                                    <Row>
                                        <Col span={12}>
                                            <Form.Item
                                                {...restField}
                                                name={[name, 'library']}
                                                label={
                                                    <Space>
                                                        <FaTrash title="Removes this library from the list."
                                                                 onClick={() => remove(name)} className="ot-action"/>
                                                        Libray name
                                                    </Space>
                                                }
                                                rules={
                                                    [{required: true, message: 'Library name is required'}]
                                                }
                                            >
                                                <Input/>
                                            </Form.Item>
                                        </Col>
                                        <Col span={12}>
                                            <Form.Item
                                                {...restField}
                                                name={[name, 'required']}
                                                label="Required"
                                                labelCol={{span: 4}}
                                                wrapperCol={{offset: 1}}
                                            >
                                                <Switch/>
                                            </Form.Item>
                                        </Col>
                                    </Row>
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'lastSupported']}
                                        label="Last supported version"
                                        labelCol={{span: 4}}
                                    >
                                        <Input style={{width: '6em'}}/>
                                    </Form.Item>
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'lastDeprecated']}
                                        label="Last deprecated version"
                                        labelCol={{span: 4}}
                                    >
                                        <Input style={{width: '6em'}}/>
                                    </Form.Item>
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'lastUnsupported']}
                                        label="Last unsupported version"
                                        labelCol={{span: 4}}
                                    >
                                        <Input style={{width: '6em'}}/>
                                    </Form.Item>
                                </Space>
                            ))}
                            <Form.Item>
                                <Button type="dashed" onClick={() => add({})} block icon={<FaPlus/>}>
                                    Add library
                                </Button>
                            </Form.Item>
                        </>
                    )}
                </Form.List>
            </SettingsForm>
        </>
    )
}

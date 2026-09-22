import {Button, Form, InputNumber, Select, Space, Typography} from "antd";
import {prefixedFormName} from "@components/form/formUtils";
import {FaPlus, FaTrash} from "react-icons/fa";
import SelectAutoDisablingBranchPatternsMode from "@components/extension/stale/SelectAutoDisablingBranchPatternsMode";

export default function PropertyForm({prefix}) {

    return (
        <>
            <Form.List
                label="Patterns"
                name={prefixedFormName(prefix, 'items')}
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
                                    <Space
                                        key={key}
                                        orientation="vertical"
                                        className="ot-form-list-item"
                                    >
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'includes']}
                                            label={
                                                <Space>
                                                    <FaTrash onClick={() => remove(name)}/>
                                                    Includes
                                                </Space>
                                            }
                                        >
                                            <Select mode="tags"/>
                                        </Form.Item>
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'excludes']}
                                            label="Excludes"
                                        >
                                            <Select mode="tags"/>
                                        </Form.Item>
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'mode']}
                                            label="Mode"
                                        >
                                            <SelectAutoDisablingBranchPatternsMode/>
                                        </Form.Item>
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'keepLast']}
                                            label="Keep last"
                                            extra="Number of branches to keep (0 = disable all). Applies to both Disable and Keep last modes. Branches are sorted using semantic versioning."
                                        >
                                            <InputNumber min={0}/>
                                        </Form.Item>
                                    </Space>
                                </>
                            ))}
                            <Button type="dashed" onClick={() => add()} block>
                                <Space>
                                    <FaPlus/>
                                    <Typography.Text>Add pattern</Typography.Text>
                                </Space>
                            </Button>
                        </div>
                    </>
                )}
            </Form.List>
        </>
    )
}
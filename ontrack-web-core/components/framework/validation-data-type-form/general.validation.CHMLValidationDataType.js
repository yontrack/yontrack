import {Checkbox, Form, InputNumber, Space, Typography} from "antd";
import SelectCHMLLevel from "@components/framework/validation-run-data-form/SelectCHMLLevel";

export default function CHMLValidationDataType({prefix, ...config}) {
    return (
        <>
            <Space orientation="vertical">
                {/*Failed if number of LEVEL issues is &ge; to COUNT*/}
                <Space align="baseline">
                    <Typography.Text>Failed if # of</Typography.Text>
                    <Form.Item
                        name={[prefix, 'failedLevel']}
                        initialValue={config?.failedLevel}
                    >
                        <SelectCHMLLevel/>
                    </Form.Item>
                    <Typography.Text>issues is &ge;</Typography.Text>
                    <Form.Item
                        name={[prefix, 'failedValue']}
                        initialValue={config?.failedValue}
                    >
                        <InputNumber min={0} style={{width: '4em'}}/>
                    </Form.Item>
                </Space>
                {/*Warning if number of LEVEL issues is &ge; to COUNT*/}
                <Space align="baseline">
                    <Typography.Text>Warning if # of</Typography.Text>
                    <Form.Item
                        name={[prefix, 'warningLevel']}
                        initialValue={config?.warningLevel}
                    >
                        <SelectCHMLLevel/>
                    </Form.Item>
                    <Typography.Text>issues is &ge;</Typography.Text>
                    <Form.Item
                        name={[prefix, 'warningValue']}
                        initialValue={config?.warningValue}
                    >
                        <InputNumber min={0} style={{width: '4em'}}/>
                    </Form.Item>
                </Space>
                {/*A WARNING run counts as passed for the auto promotion only*/}
                <Form.Item
                    name={[prefix, 'warningPassesAutoPromotion']}
                    initialValue={config?.warningPassesAutoPromotion ?? false}
                    valuePropName="checked"
                >
                    <Checkbox>A warning counts as passed for auto-promotion</Checkbox>
                </Form.Item>
            </Space>

        </>
    )
}
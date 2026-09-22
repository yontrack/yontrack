import {Form, Input, InputNumber, Space, Typography} from "antd";
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
                        initialValue={config?.failedLevel?.level}
                    >
                        <SelectCHMLLevel/>
                    </Form.Item>
                    <Typography.Text>issues is &ge;</Typography.Text>
                    <Form.Item
                        name={[prefix, 'failedValue']}
                        initialValue={config?.failedLevel?.value}
                    >
                        <InputNumber min={0} style={{width: '4em'}}/>
                    </Form.Item>
                </Space>
                {/*Warning if number of LEVEL issues is &ge; to COUNT*/}
                <Space align="baseline">
                    <Typography.Text>Warning if # of</Typography.Text>
                    <Form.Item
                        name={[prefix, 'warningLevel']}
                        initialValue={config?.warningLevel?.level}
                    >
                        <SelectCHMLLevel/>
                    </Form.Item>
                    <Typography.Text>issues is &ge;</Typography.Text>
                    <Form.Item
                        name={[prefix, 'warningValue']}
                        initialValue={config?.warningLevel?.value}
                    >
                        <InputNumber min={0} style={{width: '4em'}}/>
                    </Form.Item>
                </Space>
            </Space>

        </>
    )
}
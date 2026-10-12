import {Form, Select} from "antd";

export default function VersionPatternRuleForm() {
    return (
        <>
            <Form.Item
                name={['ruleConfig', 'includes']}
                label="Includes regular expressions"
                extra="The build version must match one of them in full. None means any version."
            >
                <Select
                    mode="tags"
                />
            </Form.Item>
            <Form.Item
                name={['ruleConfig', 'excludes']}
                label="Excludes regular expressions"
                extra="The build version must match none of them in full."
            >
                <Select
                    mode="tags"
                />
            </Form.Item>
        </>
    )
}

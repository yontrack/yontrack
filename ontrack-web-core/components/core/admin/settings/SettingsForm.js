import {Button, Form, Space, Spin} from "antd";
import {callGraphQL} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {useState} from "react";
import FormErrors from "@components/form/FormErrors";
import {getGraphQLErrors} from "@components/services/graphql-utils";

export default function SettingsForm({id, values, debug, onValuesChange, children}) {

    const [saving, setSaving] = useState(false)
    const [formErrors, setFormErrors] = useState([]);
    const [form] = Form.useForm()

    const onFinish = (values) => {
        setFormErrors([])
        setSaving(true)
        callGraphQL({
            query: gql`
                mutation SaveSettings(
                    $id: String!,
                    $values: JSON!,
                ) {
                    saveSettings(input: {
                        id: $id,
                        values: $values,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            variables: {id, values},
        }).then(data => {
            const errors = getGraphQLErrors(data, 'saveSettings')
            if (errors.length > 0) {
                setFormErrors(errors)
            }
        }).finally(() => {
            setSaving(false)
        })
    }

    const resetForm = () => {
        form.resetFields()
    }

    return (
        <>
            {
                debug &&
                JSON.stringify(values)
            }
            <Form
                form={form}
                labelCol={{span: 6}}
                initialValues={values}
                onFinish={onFinish}
                disabled={saving}
                onValuesChange={onValuesChange}
            >
                {children}
                <Form.Item
                    wrapperCol={{offset: 6}}
                >
                    <Space>
                        <Button type="primary" htmlType="submit" disabled={saving}>
                            {saving && <Spin/>}
                            Submit
                        </Button>
                        <Button onClick={resetForm} disabled={saving}>
                            Reset
                        </Button>
                    </Space>
                </Form.Item>
            </Form>
            <FormErrors errors={formErrors}/>
        </>
    )
}
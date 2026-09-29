import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {gql} from "graphql-request";
import {Button, Form, Input} from "antd";
import {callGraphQL} from "@components/services/GraphQL";
import {useState} from "react";
import ConnectionResult from "@components/configurations/ConnectionResult";

export const useJenkinsConfigurationDialog = ({onSuccess}) => {
    return useFormDialog({
        onSuccess,
        init: (form, {config}) => {
            if (config) {
                form.setFieldsValue(config)
            }
        },
        query: ({creation}) => creation ? gql`
            mutation CreateJenkinsConfiguration(
                $name: String!,
                $url: String!,
                $user: String,
                $password: String,
            ) {
                createJenkinsConfiguration(input: {
                    name: $name,
                    url: $url,
                    user: $user,
                    password: $password,
                }) {
                    errors {
                        message
                    }
                }
            }
        ` : gql`
            mutation UpdateJenkinsConfiguration(
                $name: String!,
                $url: String!,
                $user: String,
                $password: String,
            ) {
                updateJenkinsConfiguration(input: {
                    name: $name,
                    url: $url,
                    user: $user,
                    password: $password,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: ({creation}) => creation ? 'createJenkinsConfiguration' : 'updateJenkinsConfiguration',
    })
}

export default function JenkinsConfigurationDialog({jenkinsConfigurationDialog}) {

    const [connectionResult, setConnectionResult] = useState()

    const onTestConfig = () => {
        setConnectionResult(undefined)
        callGraphQL({
            query: gql`
                mutation TestJenkinsConfiguration(
                    $name: String!,
                    $url: String!,
                    $user: String,
                    $password: String,
                ) {
                    testJenkinsConfiguration(input: {
                        name: $name,
                        url: $url,
                        user: $user,
                        password: $password,
                    }) {
                        connectionResult {
                            type
                            message
                        }
                        errors {
                            message
                        }
                    }
                }
            `,
            variables: jenkinsConfigurationDialog.form.getFieldsValue(true),
        }).then(data => {
            const node = data.testJenkinsConfiguration
            if (node.errors) {
                const {message} = node.errors[0]
                setConnectionResult({
                    type: 'ERROR',
                    message,
                })
            } else {
                setConnectionResult(node.connectionResult)
            }
        })
    }

    return (
        <>
            <FormDialog
                extraButtons={
                    <Button type="default" onClick={onTestConfig}>
                        Test
                    </Button>
                }
                dialog={jenkinsConfigurationDialog}
            >
                <Form.Item
                    name="name"
                    label="Configuration name"
                    rules={[{required: true, message: 'Name is required.',},]}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="url"
                    label="Jenkins URL"
                    rules={[{required: true, message: 'URL is required.',},]}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="user"
                    label="Jenkins username"
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="password"
                    label="Jenkins password"
                >
                    <Input.Password/>
                </Form.Item>
                <ConnectionResult connectionResult={connectionResult}/>
            </FormDialog>
        </>
    )
}

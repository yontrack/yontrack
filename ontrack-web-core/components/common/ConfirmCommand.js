import {Command} from "@components/common/Commands";
import {Modal, Space} from "antd";
import FormErrors from "@components/form/FormErrors";
import {getGraphQLErrors} from "@components/services/graphql-utils";
import {useState} from "react";
import {callGraphQL} from "@components/services/GraphQL";

const {confirm} = Modal

export default function ConfirmCommand({
                                           icon,
                                           text,
                                           confirmTitle,
                                           confirmText,
                                           confirmOkText,
                                           confirmOkType,
                                           gqlQuery,
                                           gqlVariables,
                                           gqlUserNode,
                                           onSuccess,
                                       }) {

    const [errors, setErrors] = useState([])
    const onAction = () => {
        confirm({
            title: confirmTitle,
            content: <Space orientation="vertical">
                {confirmText}
                <FormErrors errors={errors}/>
            </Space>,
            okText: confirmOkText,
            okType: confirmOkType,
            onCancel: () => {
            },
            onOk: (close) => {
                return callGraphQL({
                    query: gqlQuery,
                    variables: gqlVariables,
                }).then(data => {
                    const errors = getGraphQLErrors(data, gqlUserNode)
                    if (errors.length > 0) {
                        setErrors(errors)
                    } else {
                        close()
                        // On success
                        if (onSuccess) onSuccess()
                    }
                })
            },
        })
    }

    return (
        <>
            <Command
                icon={icon}
                text={text}
                action={onAction}
            />
        </>
    )
}
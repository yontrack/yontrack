import {Command} from "@components/common/Commands";
import {Modal, Space} from "antd";
import FormErrors from "@components/form/FormErrors";
import {getUserErrors} from "@components/services/graphql-utils";
import {useState} from "react";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";

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

    const client = useGraphQLClient()

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
                return client.request(
                    gqlQuery,
                    gqlVariables
                ).then(data => {
                    const errors = getUserErrors(data[gqlUserNode])
                    if (errors) {
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
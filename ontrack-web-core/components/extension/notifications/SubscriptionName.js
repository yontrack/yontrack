import {message, Spin, Typography} from "antd";
import {useState} from "react";
import {FaPencilAlt} from "react-icons/fa";
import {callGraphQL} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {getGraphQLErrors} from "@components/services/graphql-utils";

export default function SubscriptionName({subscription, text, entity, managePermission, onRenamed}) {

    const [messageApi, contextHolder] = message.useMessage()
    const [changing, setChanging] = useState(false)

    const onChange = async (value) => {
        const data = await callGraphQL({
            query: gql`
                mutation RenameSubscription(
                    $projectEntity: ProjectEntityIDInput,
                    $name: String!,
                    $newName: String!,
                ) {
                    renameSubscription(input: {
                        projectEntity: $projectEntity,
                        name: $name,
                        newName: $newName,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            variables: {
                projectEntity: entity,
                name: subscription.name,
                newName: value,
            },
        })
        const errors = getGraphQLErrors(data, 'renameSubscription')
        if (errors.length > 0) {
            messageApi.error(errors[0])
        } else if (onRenamed) {
            onRenamed(value)
        }
    }

    return (
        <>
            {contextHolder}
            <Typography.Text
                editable={managePermission ? {
                    onChange: onChange,
                    icon: changing ? <Spin size="small"/> : <FaPencilAlt size={12}/>,
                } : false}
            >
                {text ?? subscription.name}
            </Typography.Text>
        </>
    )
}
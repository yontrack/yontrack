import {Command} from "@components/common/Commands";
import {FaTrash} from "react-icons/fa";
import {Modal, Typography} from "antd";
import {gql} from "graphql-request";
import {callGraphQL} from "@components/services/GraphQL";
import {processGraphQLErrors} from "@components/services/graphql-utils";
import {useMessageApi} from "@components/providers/MessageProvider";
import {labelDisplay} from "@components/labels/LabelChip";

const {confirm} = Modal

/**
 * Deletion of a label, after a confirmation which says how many projects carry
 * it - deleting a label removes it from every project it is set on, and the
 * count is the only way to know how much is being undone.
 *
 * Indicator portfolios pointing at the label are deliberately not mentioned:
 * they degrade gracefully, simply listing no project.
 */
export default function LabelDeleteCommand({label, onChange}) {

    const messageApi = useMessageApi()

    const display = labelDisplay(label)

    const projects = () => {
        const count = label?.projectCount ?? 0
        if (count === 0) {
            return "No project carries this label."
        } else if (count === 1) {
            return "1 project carries this label and will lose it."
        } else {
            return `${count} projects carry this label and will lose it.`
        }
    }

    const onAction = () => {
        confirm({
            title: `Do you really want to delete the "${display}" label?`,
            content: <Typography.Text>{projects()}</Typography.Text>,
            okText: "Delete",
            okType: "danger",
            onCancel: () => {
            },
            onOk: async () => {
                const data = await callGraphQL({
                    query: gql`
                        mutation DeleteLabel($id: Int!) {
                            deleteLabel(input: {id: $id}) {
                                errors {
                                    message
                                }
                            }
                        }
                    `,
                    variables: {id: Number(label.id)},
                })
                if (processGraphQLErrors(data, 'deleteLabel', messageApi)) {
                    if (onChange) onChange()
                }
            },
        })
    }

    return (
        <>
            <Command
                icon={<FaTrash/>}
                title={`Delete the ${display} label`}
                testId={`label-delete-${display}`}
                action={onAction}
            />
        </>
    )
}

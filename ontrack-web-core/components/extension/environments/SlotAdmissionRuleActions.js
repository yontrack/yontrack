import {Space, message} from "antd";
import {FaPencilAlt} from "react-icons/fa";
import {useState} from "react";
import {gql} from "graphql-request";
import {Command} from "@components/common/Commands";
import InlineConfirmCommand from "@components/common/InlineConfirmCommand";
import {callGraphQL} from "@components/services/GraphQL";
import {isAuthorized} from "@components/common/authorizations";
import {processGraphQLErrors} from "@components/services/graphql-utils";

/**
 * Editing and deleting one configured admission rule.
 *
 * Both are new here in their own way (#1793):
 *
 * - **Edit** did not exist at all. A rule could only be deleted and added back, which gives it a new
 *   id - and a deployment's stored answers to a rule are keyed on that id, so "delete and re-add"
 *   quietly detached every approval already given.
 * - **Delete** existed with no permission check in front of it. The backend has always refused it
 *   without `SlotUpdate`, so this was a button that produced an error rather than a button that was
 *   not there; the rest of Yontrack hides what the reader may not do.
 *
 * @param {Object} slot The slot the rule belongs to, carrying its authorizations.
 * @param {Object} rule The configured rule.
 * @param {Object} dialog The shared rule dialog, so that editing several rules does not mount one
 *   dialog per row - `Form.Item` names its control after the field, and a closed Ant Design modal
 *   stays in the document, so N mounted dialogs means N inputs with the same id.
 * @param {function} onChange Called after a change.
 */
export default function SlotAdmissionRuleActions({slot, rule, dialog, onChange}) {

    const [messageApi, contextHolder] = message.useMessage()
    const [loading, setLoading] = useState(false)

    const canEdit = isAuthorized(slot, "slot", "edit")

    const deleteRule = async () => {
        setLoading(true)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation DeleteSlotAdmissionRuleConfig($id: String!) {
                        deleteSlotAdmissionRuleConfig(input: {
                            id: $id
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {id: rule.id},
            })
            if (processGraphQLErrors(data, 'deleteSlotAdmissionRuleConfig', messageApi)) {
                if (onChange) onChange()
            }
        } finally {
            setLoading(false)
        }
    }

    if (!canEdit) return null

    return (
        <>
            {contextHolder}
            <Space>
                <Command
                    icon={<FaPencilAlt/>}
                    title="Edit this rule"
                    testId={`slot-rule-edit-${rule.id}`}
                    action={() => dialog.start({slot, rule})}
                />
                <InlineConfirmCommand
                    title="Delete this rule"
                    confirm="Do you really want to delete this rule?"
                    onConfirm={deleteRule}
                    loading={loading}
                />
            </Space>
        </>
    )
}

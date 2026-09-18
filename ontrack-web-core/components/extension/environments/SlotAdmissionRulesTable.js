import {Button, Space, Table} from "antd";
import {gql} from "graphql-request";
import SlotAdmissionRuleConfigDialog, {
    useSlotAdmissionRuleConfigDialog
} from "@components/extension/environments/SlotAdmissionRuleConfigDialog";
import {useQuery} from "@components/services/GraphQL";
import {FaPlus} from "react-icons/fa";
import {isAuthorized} from "@components/common/authorizations";
import SlotAdmissionRuleSummary from "@components/extension/environments/SlotAdmissionRuleSummary";
import SlotAdmissionRuleActions from "@components/extension/environments/SlotAdmissionRuleActions";

/**
 * The configured admission rules of a slot - the first half of the slot page's **Setup** tab.
 *
 * One dialog is mounted for the whole table rather than one per row: an Ant Design `Form.Item` names
 * its control after its field, a closed modal stays in the document, and N mounted copies of this
 * dialog would put N inputs with `id="description"` in the page - after which `getByLabel` finds
 * whichever is first, which may be an invisible one.
 */
export default function SlotAdmissionRulesTable({slot, reloadCount = 0, onChange}) {

    const {data, loading} = useQuery(
        gql`
            query SlotAdmissionRules($id: String!) {
                slotById(id: $id) {
                    admissionRules {
                        id
                        name
                        description
                        ruleId
                        ruleConfig
                    }
                }
            }
        `,
        {
            variables: {id: slot.id},
            deps: [slot.id, reloadCount],
            dataFn: data => data.slotById.admissionRules,
            initialData: [],
        }
    )

    const dialog = useSlotAdmissionRuleConfigDialog({
        onSuccess: onChange,
    })

    return (
        <>
            <SlotAdmissionRuleConfigDialog dialog={dialog}/>
            <Table
                dataSource={data ?? []}
                loading={loading}
                rowKey={rule => rule.id}
                pagination={false}
                size="small"
                data-testid={`slot-rules-${slot.id}`}
                onRow={rule => ({'data-testid': `slot-rule-${rule.id}`})}
                footer={() =>
                    <Space>
                        {
                            isAuthorized(slot, "slot", "edit") &&
                            <Button
                                icon={<FaPlus/>}
                                data-testid="slot-add-rule"
                                onClick={() => dialog.start({slot})}
                            >
                                Add admission rule
                            </Button>
                        }
                    </Space>
                }
            >
                <Table.Column
                    key="name"
                    title="Name"
                    dataIndex="name"
                />
                <Table.Column
                    key="description"
                    title="Description"
                    dataIndex="description"
                />
                <Table.Column
                    key="ruleId"
                    title="Rule"
                    render={(_, rule) => <SlotAdmissionRuleSummary ruleId={rule.ruleId} ruleConfig={rule.ruleConfig}/>}
                />
                <Table.Column
                    key="actions"
                    title="Actions"
                    render={(_, rule) => <SlotAdmissionRuleActions
                        slot={slot}
                        rule={rule}
                        dialog={dialog}
                        onChange={onChange}
                    />}
                />
            </Table>
        </>
    )
}

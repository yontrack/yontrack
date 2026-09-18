import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Form, Input} from "antd";
import SelectSlotAdmissionRule from "@components/extension/environments/SelectSlotAdmissionRule";
import Well from "@components/common/Well";
import {useState} from "react";
import SlotAdmissionRuleForm from "@components/extension/environments/SlotAdmissionRuleForm";
import {gql} from "graphql-request";

/**
 * Adding an admission rule to a slot, or editing one that is already there.
 *
 * One dialog for both, because they are the same six fields and a second dialog would be a second
 * place for them to drift. Which of the two it is comes from the context: `start({slot})` adds,
 * `start({slot, rule})` edits.
 *
 * The mutation's shape says the same thing - `saveSlotAdmissionRuleConfig` takes *either* an `id`
 * (this rule) *or* a `slotId` (a new rule on that slot) and refuses both, so the dialog sends one or
 * the other rather than a flag.
 */
export const useSlotAdmissionRuleConfigDialog = ({onSuccess}) => {

    const [currentValues, setCurrentValues] = useState({})

    return useFormDialog({
        onSuccess: onSuccess,
        init: (form, context) => {
            const rule = context?.rule
            setCurrentValues({...rule})
            form.setFieldValue('name', rule?.name)
            form.setFieldValue('description', rule?.description)
            form.setFieldValue('ruleId', rule?.ruleId)
            form.setFieldValue('ruleConfig', rule?.ruleConfig)
        },
        currentValues,
        setCurrentValues,
        query: gql`
            mutation SaveAdmissionRuleConfig(
                $id: String,
                $slotId: String,
                $name: String,
                $description: String!,
                $ruleId: String!,
                $ruleConfig: JSON!,
            ) {
                saveSlotAdmissionRuleConfig(input: {
                    id: $id,
                    slotId: $slotId,
                    name: $name,
                    description: $description,
                    ruleId: $ruleId,
                    ruleConfig: $ruleConfig,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'saveSlotAdmissionRuleConfig',
        prepareValues: (values, context) => {
            const editing = !!context?.rule
            return {
                ...values,
                description: values.description ?? '',
                id: editing ? context.rule.id : null,
                slotId: editing ? null : context.slot.id,
            }
        },
    })
}

export default function SlotAdmissionRuleConfigDialog({dialog}) {

    const onValuesChange = (changedValues /*, allValues*/) => {
        if (changedValues.hasOwnProperty('ruleId')) {
            const ruleId = changedValues.ruleId
            if (ruleId !== dialog.currentValues.ruleId) {
                dialog.form.setFieldValue('ruleConfig', null)
                dialog.setCurrentValues({...dialog.currentValues, ruleId, ruleConfig: null})
            }
        }
    }

    return (
        <>
            <FormDialog dialog={dialog} onValuesChange={onValuesChange} id="slot-admission-rule-dialog">
                <Form.Item
                    name="name"
                    label="Name"
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="description"
                    label="Description"
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="ruleId"
                    label="Rule configuration"
                    rules={[
                        {
                            required: true,
                            message: 'Rule is required',
                        },
                    ]}
                >
                    <SelectSlotAdmissionRule/>
                </Form.Item>
                {
                    dialog.currentValues.ruleId &&
                    <Form.Item
                        name="ruleConfig"
                        label="Configuration"
                    >
                        <Well>
                            <SlotAdmissionRuleForm
                                ruleId={dialog.currentValues.ruleId}
                            />
                        </Well>
                    </Form.Item>
                }
            </FormDialog>
        </>
    )
}

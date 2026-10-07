import {Form, Input, Switch} from "antd"
import {gqlSlotUpdate} from "@components/extension/environments/slot/slotGraphQL"
import FormDialog, {useFormDialog} from "@components/form/FormDialog"

/**
 * Edits what a slot is besides its rules and workflows: its description, and whether it admits
 * agents.
 *
 * @param {function} onSuccess Called once the slot is saved
 */
export const useSlotSettingsDialog = ({onSuccess}) => {
    return useFormDialog({
        init: (form, {slot}) => {
            form.setFieldsValue({
                description: slot.description,
                agentsAdmitted: !!slot.agentsAdmitted,
            })
        },
        prepareValues: (values, {slot}) => ({
            slotId: slot.id,
            description: values.description ?? "",
            agentsAdmitted: !!values.agentsAdmitted,
        }),
        query: gqlSlotUpdate,
        userNode: 'updateSlot',
        onSuccess,
    })
}

export default function SlotSettingsDialog({dialog}) {
    return (
        <FormDialog dialog={dialog} id="slot-settings-dialog">
            <Form.Item
                name="description"
                label="Description"
            >
                <Input.TextArea data-testid="slot-settings-description"/>
            </Form.Item>
            <Form.Item
                name="agentsAdmitted"
                label="Agents admitted"
                valuePropName="checked"
                extra="A registered agent may start, run and finish a deployment in this slot, provided its owner may. An agent never satisfies a manual approval: a person does."
            >
                <Switch data-testid="slot-settings-agents-admitted"/>
            </Form.Item>
        </FormDialog>
    )
}

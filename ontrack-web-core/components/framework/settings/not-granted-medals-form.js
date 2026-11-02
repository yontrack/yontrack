import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, Switch} from "antd";

export default function NotGrantedMedalsForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="enabled"
                    label="Shown"
                    extra="Shows, in the branch builds view, a medal for each auto-promoted level a build does not have yet. A project or a branch can override it with a property."
                >
                    <Switch/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}

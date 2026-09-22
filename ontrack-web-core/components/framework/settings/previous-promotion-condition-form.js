import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Alert, Form, Switch} from "antd";

export default function PreviousPromotionConditionForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item>
                    <Alert
                        type="warning"
                        title="These settings are global and should be used with caution. Instead, try to define your promotion constraints using code at each branch level."
                    />
                </Form.Item>
                <Form.Item
                    name="previousPromotionRequired"
                    label="Previous promotion required"
                    extra="Makes a promotion conditional based on the fact that a previous promotion has been granted."
                >
                    <Switch/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
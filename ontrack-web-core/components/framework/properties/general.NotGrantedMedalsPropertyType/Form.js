import {Form, Switch} from "antd";
import {prefixedFormName} from "@components/form/formUtils";

export default function PropertyForm({prefix}) {
    return (
        <>
            <Form.Item
                label="Shown"
                extra="If set, the branch builds view shows a medal for each auto-promoted level a build does not have yet. Deleting the property returns to the value of the next level: the project, then the global settings."
                name={prefixedFormName(prefix, 'enabled')}
                initialValue={true}
            >
                <Switch/>
            </Form.Item>
        </>
    )
}

import {Form, Switch} from "antd";
import {prefixedFormName} from "@components/form/formUtils";

export default function PropertyForm({prefix}) {

    return (
        <>
            <Form.Item
                label="Admitted"
                extra="If set, a registered agent may promote a build to this level, provided its owner may. Auto-promotion is not concerned."
                name={prefixedFormName(prefix, 'admitted')}
                initialValue={true}
            >
                <Switch/>
            </Form.Item>
        </>
    )
}

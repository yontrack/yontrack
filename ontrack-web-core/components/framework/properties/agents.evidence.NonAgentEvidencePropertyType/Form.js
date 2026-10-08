import {Form, Switch} from "antd";
import {prefixedFormName} from "@components/form/formUtils";
import LicenceNotice from "./LicenceNotice";

export default function PropertyForm({prefix}) {

    return (
        <>
            <LicenceNotice/>
            <Form.Item
                label="Non-agents only"
                extra="If set, an agent may neither create a validation run on this stamp nor change the status of one of its runs: its attempts are refused. Agents produce configuration and rules, never numbers."
                name={prefixedFormName(prefix, 'enabled')}
                initialValue={true}
            >
                <Switch/>
            </Form.Item>
        </>
    )
}

import {Form, Input} from "antd";
import {prefixedFormName} from "@components/form/formUtils";

export default function OntrackValidationNotificationChannelForm({prefix}) {
    return (
        <>
            {/*@APIDescription("[template] Name of the branch to validate. If not provided, looks for the event's branch if available.")*/}
            {/*val branch: String? = null,*/}
            {/*@APIDescription("[template] Name of the build to validate. If not provided, looks for the event's build if available.")*/}
            {/*val build: String? = null,*/}
            <Form.Item
                name={prefixedFormName(prefix, 'project')}
                label="Project"
                extra="[template] Name of the project to validate. If not provided, looks for the event's project if available."
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'branch')}
                label="Branch"
                extra="[template] Name of the branch to validate. If not provided, looks for the event's branch if available."
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'build')}
                label="Build"
                extra="[template] Name of the build to validate. If not provided, looks for the event's build if available."
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'validation')}
                label="Validation"
                extra="Name of the validation stamp to use."
                rules={[{required: true, message: 'Validation is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'status')}
                label="Status"
                extra="[template] Status of the validation run, must render to a valid validation run status ID (e.g. PASSED, FAILED). Defaults to PASSED."
            >
                <Input/>
            </Form.Item>
        </>
    )
}
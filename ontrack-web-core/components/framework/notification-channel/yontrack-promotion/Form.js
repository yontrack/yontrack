import {Form, Input, Switch} from "antd";
import {prefixedFormName} from "@components/form/formUtils";
import DurationPicker from "@components/common/DurationPicker";
import PromotionRunFieldsFormList from "@components/extension/notifications/PromotionRunFieldsFormList";

export default function YontrackPromotionNotificationChannelForm({prefix}) {
    return (
        <>
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
                name={prefixedFormName(prefix, 'promotion')}
                label="Promotion"
                extra="Name of the promotion level to use."
                rules={[{required: true, message: 'Promotion level is required.'}]}
            >
                <Input/>
            </Form.Item>
            <PromotionRunFieldsFormList prefix={prefix}/>
            <Form.Item
                name={prefixedFormName(prefix, 'waitForPromotion')}
                label="Wait for promotion"
                extra="Waiting for the promotion level associated notifications to be completed"
            >
                <Switch/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'waitForPromotionTimeout')}
                label="Wait for promotion timeout"
                extra="Timeout when waiting for the promotion level associated notifications to be completed"
            >
                <DurationPicker inMilliseconds={false} maxUnit="hour"/>
            </Form.Item>
        </>
    )
}
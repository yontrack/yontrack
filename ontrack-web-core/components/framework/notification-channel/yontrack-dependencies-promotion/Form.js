import {Form, Input, Switch} from "antd";
import {prefixedFormName} from "@components/form/formUtils";
import DurationPicker from "@components/common/DurationPicker";
import PromotionRunFieldsFormList from "@components/extension/notifications/PromotionRunFieldsFormList";

/**
 * An empty input removes the filter: on the server, an empty qualifier would select the unqualified links only.
 */
const emptyAsAbsent = (value) => value === '' ? undefined : value

export default function YontrackDependenciesPromotionNotificationChannelForm({prefix}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'project')}
                label="Project"
                extra="Regular expression which must match the whole name of the project of the linked build. If empty, all projects are selected."
                normalize={emptyAsAbsent}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'qualifier')}
                label="Qualifier"
                extra="Regular expression which must match the whole qualifier of the link. If empty, all links are selected. Use ^$ to select the unqualified links only."
                normalize={emptyAsAbsent}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'promotion')}
                label="Promotion"
                extra="Name of the promotion level to use, the same for all the linked builds."
                rules={[{required: true, message: 'Promotion level is required.'}]}
            >
                <Input/>
            </Form.Item>
            <PromotionRunFieldsFormList prefix={prefix}/>
            <Form.Item
                name={prefixedFormName(prefix, 'waitForPromotion')}
                label="Wait for promotion"
                extra="Waiting for the notifications of the promotion runs created by this notification to be completed"
            >
                <Switch/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'waitForPromotionTimeout')}
                label="Wait for promotion timeout"
                extra="Timeout when waiting for the notifications of the created promotion runs to be completed"
            >
                <DurationPicker inMilliseconds={false} maxUnit="hour"/>
            </Form.Item>
        </>
    )
}

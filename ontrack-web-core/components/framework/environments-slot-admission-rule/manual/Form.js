import {Form, Input, Select} from "antd";

/**
 * Configuring a manual approval.
 *
 * The backend has accepted `users` and `groups` since the rule was written - `checkData` refuses an
 * approval from anybody outside them - but this form exposed only the message, so the restriction
 * could be set through the API or as code and never through the UI (#1793). A rule whose whole
 * point is "somebody in particular must say yes" is not configurable without them.
 *
 * Both are free-text tag inputs rather than pickers over the accounts and groups of the instance:
 * the values stored are names, a configuration written as code names accounts that may not exist
 * yet, and a picker would quietly refuse to reproduce what such a configuration already holds.
 * Empty means "anybody who can act on the deployment", which is what the backend does with an empty
 * list.
 */
export default function ManualRuleForm() {
    return (
        <>
            <Form.Item
                name={['ruleConfig', 'message']}
                label="Message"
                extra="Shown to whoever is asked to approve the deployment."
                rules={[{required: true, message: 'Message is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={['ruleConfig', 'users']}
                label="Users"
                extra="Only these users may approve. Leave empty to allow anyone who can act on the deployment."
            >
                <Select data-testid="manual-users" mode="tags" tokenSeparators={[',']}/>
            </Form.Item>
            <Form.Item
                name={['ruleConfig', 'groups']}
                label="Groups"
                extra="Only members of these groups may approve. Leave empty to allow any group."
            >
                <Select data-testid="manual-groups" mode="tags" tokenSeparators={[',']}/>
            </Form.Item>
        </>
    )
}

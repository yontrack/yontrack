import {Form, InputNumber, Select} from "antd";
import {prefixedFormName} from "@components/form/formUtils";

/**
 * Setting the assisted change of a build by hand, as the CI does: the value is then never recomputed.
 */
export default function PropertyForm({prefix}) {
    return (
        <>
            <Form.Item
                label="Assistants"
                extra="Names of the assistants (agent kinds) which helped write the commits of the build, like Claude Code or Codex. None means the build is not assisted."
                name={prefixedFormName(prefix, 'assistants')}
            >
                <Select mode="tags" aria-label="Assistants"/>
            </Form.Item>
            <Form.Item
                label="Assisted commits"
                extra="Number of commits written with an assistant"
                name={prefixedFormName(prefix, 'assistedCommits')}
                initialValue={0}
            >
                <InputNumber min={0} aria-label="Assisted commits"/>
            </Form.Item>
            <Form.Item
                label="Total commits"
                extra="Number of commits in the change of the build"
                name={prefixedFormName(prefix, 'totalCommits')}
                initialValue={0}
            >
                <InputNumber min={0} aria-label="Total commits"/>
            </Form.Item>
            <Form.Item
                label="Session links"
                extra="Links to the agent sessions behind the commits"
                name={prefixedFormName(prefix, 'sessionLinks')}
            >
                <Select mode="tags" aria-label="Session links"/>
            </Form.Item>
        </>
    )
}

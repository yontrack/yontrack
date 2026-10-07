import {AutoComplete} from "antd";
import {AGENT_TOOLS} from "@components/core/admin/agents/agentsModel";

/**
 * Tool behind an agent: one of the known tools, or any other name typed in.
 */
export default function AgentToolInput({id, value, onChange}) {
    return (
        <AutoComplete
            id={id}
            value={value}
            onChange={onChange}
            options={AGENT_TOOLS.map(tool => ({value: tool}))}
            placeholder="Claude Code, Codex, Copilot, Devin..."
            allowClear={true}
        />
    )
}

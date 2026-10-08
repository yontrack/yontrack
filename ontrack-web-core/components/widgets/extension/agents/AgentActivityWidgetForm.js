import {useContext} from "react";
import {Form, Segmented} from "antd";
import SelectProject from "@components/projects/SelectProject";
import SelectLabel from "@components/labels/SelectLabel";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {
    AGENT_ACTIONS_WINDOWS,
    AGENT_ACTIVITY_WIDGET_DEFAULT_WINDOW,
    agentActionsWindow,
} from "@components/extension/agents/agentActionsModel";

/**
 * Configuration of the *Agent activity* widget: its window, and optionally the projects or the
 * labels narrowing it.
 *
 * The config field `window` is renamed `days` here, not to shadow the global `window`.
 */
export default function AgentActivityWidgetForm({window: days, projects, labels}) {

    const {widgetEditionForm} = useContext(DashboardWidgetCellContext)

    return (
        <Form
            layout="vertical"
            form={widgetEditionForm}
        >
            <Form.Item
                name="window"
                label="Window"
                initialValue={agentActionsWindow(days, AGENT_ACTIVITY_WIDGET_DEFAULT_WINDOW)}
                extra="Number of days the counts are over, ending now"
            >
                <Segmented
                    options={AGENT_ACTIONS_WINDOWS.map(value => ({label: `${value} days`, value}))}
                />
            </Form.Item>
            <Form.Item
                name="projects"
                label="Projects"
                initialValue={projects ?? []}
                extra="Counts only these projects - all the projects you can see when empty"
            >
                <SelectProject multiple={true} width="100%"/>
            </Form.Item>
            <Form.Item
                name="labels"
                label="Labels"
                initialValue={labels ?? []}
                extra="Counts only the projects carrying all these labels"
            >
                <SelectLabel multiple placeholder="Labels" style={{width: "100%"}}/>
            </Form.Item>
        </Form>
    )
}

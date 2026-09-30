import {useContext} from "react";
import {Form, Switch} from "antd";
import SelectProject from "@components/projects/SelectProject";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";

export default function ProjectFindingsWidgetForm({project, showBranches}) {

    const {widgetEditionForm} = useContext(DashboardWidgetCellContext)

    return (
        <Form
            layout="vertical"
            form={widgetEditionForm}
        >
            <Form.Item
                name="project"
                label="Project"
                initialValue={project}
                extra="Project whose findings are displayed"
            >
                <SelectProject/>
            </Form.Item>
            <Form.Item
                name="showBranches"
                label="Show branches"
                initialValue={showBranches}
                extra="If checked, lists the branches having open findings, among the ones which count for the project"
            >
                <Switch/>
            </Form.Item>
        </Form>
    )
}

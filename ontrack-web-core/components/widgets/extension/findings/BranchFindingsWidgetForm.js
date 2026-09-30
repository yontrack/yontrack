import {useContext} from "react";
import {Form} from "antd";
import SelectProjectBranch from "@components/branches/SelectProjectBranch";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";

export default function BranchFindingsWidgetForm({project, branch}) {

    const {widgetEditionForm, onReceivingValuesHandler} = useContext(DashboardWidgetCellContext)

    onReceivingValuesHandler((values) => ({
        project: values.selectedBranch?.project,
        branch: values.selectedBranch?.branch,
    }))

    return (
        <Form
            layout="vertical"
            form={widgetEditionForm}
        >
            <Form.Item
                name="selectedBranch"
                label="Branch"
                initialValue={{project, branch}}
                extra="Branch whose findings are displayed"
            >
                <SelectProjectBranch/>
            </Form.Item>
        </Form>
    )
}

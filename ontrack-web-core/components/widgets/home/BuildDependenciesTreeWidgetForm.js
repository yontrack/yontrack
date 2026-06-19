import {useContext} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {Form, Input} from "antd";
import SelectProjectBranchPromotionLevel from "@components/promotionLevels/SelectProjectBranchPromotionLevel";

export default function BuildDependenciesTreeWidgetForm({title, project, branch, promotionLevel}) {

    const {widgetEditionForm, onReceivingValuesHandler} = useContext(DashboardWidgetCellContext)

    const onFormValues = (values) => ({
        title: values.title,
        project: values.promotionLevel?.project ?? '',
        branch: values.promotionLevel?.branch ?? '',
        promotionLevel: values.promotionLevel?.promotionLevel ?? '',
    })

    onReceivingValuesHandler(onFormValues)

    return (
        <Form layout="vertical" form={widgetEditionForm}>
            <Form.Item name="title" label="Title" initialValue={title ?? "Build dependencies"}>
                <Input/>
            </Form.Item>
            <Form.Item
                name="promotionLevel"
                label="Promotion level"
                initialValue={{project, branch, promotionLevel}}
            >
                <SelectProjectBranchPromotionLevel/>
            </Form.Item>
        </Form>
    )
}

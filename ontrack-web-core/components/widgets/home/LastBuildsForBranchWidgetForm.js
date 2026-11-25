import {useContext} from "react";
import {Form, Input, InputNumber} from "antd";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import SelectProjectBranch from "@components/branches/SelectProjectBranch";
import SelectMultiplePromotionLevelNames from "@components/promotionLevels/SelectMultiplePromotionLevelNames";
import SelectMultipleValidationStampsNames from "@components/validationStamps/SelectMultipleValidationStampsNames";

/**
 * Edition of the configuration of the "Last builds for a branch" widget.
 */
export default function LastBuildsForBranchWidgetForm({title, project, branch, count, promotions, validations}) {

    const {widgetEditionForm, onReceivingValuesHandler} = useContext(DashboardWidgetCellContext)

    // A cleared field comes back as null, which would not get the default of the display
    onReceivingValuesHandler((values) => ({
        title: values.title,
        project: values.selectedBranch?.project,
        branch: values.selectedBranch?.branch,
        count: values.count ?? 10,
        promotions: values.promotions ?? [],
        validations: values.validations ?? [],
    }))

    return (
        <Form
            layout="vertical"
            form={widgetEditionForm}
        >
            <Form.Item
                name="title"
                label="Title"
                extra="If not provided, a default title is displayed, based on the selected branch."
                initialValue={title}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name="selectedBranch"
                label="Branch"
                initialValue={{project, branch}}
            >
                <SelectProjectBranch/>
            </Form.Item>
            <Form.Item
                name="count"
                label="Number of builds to display"
                initialValue={count}
            >
                <InputNumber min={1} max={50} step={1}/>
            </Form.Item>
            <Form.Item
                name="promotions"
                label="Promotions to display"
                extra="All the promotions are displayed if none is selected."
                initialValue={promotions}
            >
                <SelectMultiplePromotionLevelNames/>
            </Form.Item>
            <Form.Item
                name="validations"
                label="Validations to display"
                extra="All the validations are displayed if none is selected."
                initialValue={validations}
            >
                <SelectMultipleValidationStampsNames/>
            </Form.Item>
        </Form>
    )
}

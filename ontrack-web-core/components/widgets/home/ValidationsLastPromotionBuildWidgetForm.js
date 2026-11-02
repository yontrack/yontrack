import {useContext} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {Form, Input} from "antd";
import SelectProjectBranchPromotionLevel from "@components/promotionLevels/SelectProjectBranchPromotionLevel";
import SelectMultipleValidationStampsNames from "@components/validationStamps/SelectMultipleValidationStampsNames";

export default function ValidationsLastPromotionBuildWidgetForm({title, project, branch, promotion, validations}) {

    const {widgetEditionForm} = useContext(DashboardWidgetCellContext)

    const {onReceivingValuesHandler} = useContext(DashboardWidgetCellContext)

    const onFormValues = (values) => {
        // Flattening
        return {
            title: values.title,
            validations: values.validations,
            project: values.promotionLevel.project,
            branch: values.promotionLevel.branch,
            promotion: values.promotionLevel.promotionLevel,
        }
    }

    onReceivingValuesHandler(onFormValues)

    return (
        <>
            <Form
                layout="vertical"
                form={widgetEditionForm}
            >
                <Form.Item
                    name="title"
                    label="Title"
                    extra="If not provided, a default title is displayed, based on the selected elements."
                    initialValue={title}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="promotionLevel"
                    label="Promotion level"
                    initialValue={{project, branch, promotionLevel: promotion}}
                >
                    <SelectProjectBranchPromotionLevel/>
                </Form.Item>
                <Form.Item
                    name="validations"
                    label="List of validations to display"
                    initialValue={validations}
                >
                    <SelectMultipleValidationStampsNames/>
                </Form.Item>
            </Form>
        </>
    )
}
import {Button, Form, Input, Space, Switch} from "antd";
import {useContext} from "react";
import {FaPlus, FaTrash} from "react-icons/fa";
import SelectProjectBranch from "@components/branches/SelectProjectBranch";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import SortableList, {SortableItem} from "react-easy-sort";
import {formFieldArraySwap} from "@components/form/formUtils";
import PromotionWarningPeriodInput from "@components/widgets/home/PromotionWarningPeriodInput";
import ValidationWarningPeriodInput from "@components/widgets/home/ValidationWarningPeriodInput";

export default function BranchStatusesWidgetForm({
                                                     promotionConfigs,
                                                     validationConfigs,
                                                     displayValidationResults,
                                                     displayValidationRun,
                                                     branches,
                                                     title
                                                 }) {

    const {widgetEditionForm} = useContext(DashboardWidgetCellContext)

    const onSortEnd = (oldIndex, newIndex) => {
        formFieldArraySwap(widgetEditionForm, "branches", oldIndex, newIndex)
    }

    return (
        <>
            <Form
                layout="vertical"
                form={widgetEditionForm}
            >
                <Form.Item
                    name="title"
                    label="Title"
                    initialValue={title}
                >
                    <Input/>
                </Form.Item>
                <Form.List name="promotionConfigs" initialValue={promotionConfigs}>
                    {(fields, {add, remove}) => (
                        <>
                            {fields.map(({key, name, ...restField}) => (
                                <Space
                                    key={key}
                                    className="ot-form-list-item"
                                    align="baseline"
                                >
                                    <Form.Item
                                        {...restField}
                                        name={name}
                                        label={
                                            <Space>
                                                <FaTrash onClick={() => remove(name)}/>
                                                Promotion
                                            </Space>
                                        }
                                        rules={[
                                            {
                                                required: true,
                                                message: 'Promotion config is required',
                                            },
                                        ]}
                                    >
                                        <PromotionWarningPeriodInput/>
                                    </Form.Item>
                                </Space>
                            ))}
                            <Form.Item>
                                <Button type="dashed"
                                        onClick={() => add({
                                            promotionLevel: '',
                                            period: undefined,
                                        })} block
                                        icon={<FaPlus/>}
                                >
                                    Add promotion
                                </Button>
                            </Form.Item>
                        </>
                    )}
                </Form.List>
                <Form.List name="validationConfigs" initialValue={validationConfigs}>
                    {(fields, {add, remove}) => (
                        <>
                            {fields.map(({key, name, ...restField}) => (
                                <Space
                                    key={key}
                                    className="ot-form-list-item"
                                    align="baseline"
                                >
                                    <Form.Item
                                        {...restField}
                                        name={name}
                                        label={
                                            <Space>
                                                <FaTrash onClick={() => remove(name)}/>
                                                Validation
                                            </Space>
                                        }
                                        rules={[
                                            {
                                                required: true,
                                                message: 'Validation config is required',
                                            },
                                        ]}
                                    >
                                        <ValidationWarningPeriodInput/>
                                    </Form.Item>
                                </Space>
                            ))}
                            <Form.Item>
                                <Button type="dashed"
                                        onClick={() => add({
                                            validationStamp: '',
                                            period: undefined,
                                        })} block
                                        icon={<FaPlus/>}
                                >
                                    Add validation
                                </Button>
                            </Form.Item>
                        </>
                    )}
                </Form.List>
                <Form.Item
                    name="displayValidationResults"
                    label="If checked, displays additional results with each validation, like test summary, etc."
                    initialValue={displayValidationResults}
                >
                    <Switch/>
                </Form.Item>
                <Form.Item
                    name="displayValidationRun"
                    label="If checked, displays run info"
                    initialValue={displayValidationRun}
                >
                    <Switch/>
                </Form.Item>
                <Form.List name="branches" initialValue={branches}>
                    {(fields, {add, remove}) => (
                        <>
                            <SortableList onSortEnd={onSortEnd}>
                                {fields.map(({key, name, ...restField}) => (
                                    <SortableItem key={key}>
                                        <Space
                                            key={key}
                                            className="ot-form-list-item"
                                            align="baseline"
                                        >
                                            <Form.Item
                                                {...restField}
                                                name={name}
                                                label={
                                                    <Space>
                                                        <FaTrash onClick={() => remove(name)}/>
                                                        Branch
                                                    </Space>
                                                }
                                                rules={[
                                                    {
                                                        required: true,
                                                        message: 'Branch is required',
                                                    },
                                                ]}
                                            >
                                                <SelectProjectBranch/>
                                            </Form.Item>
                                        </Space>
                                    </SortableItem>
                                ))}
                            </SortableList>
                            <Form.Item>
                                <Button type="dashed"
                                        onClick={() => add('')} block
                                        icon={<FaPlus/>}
                                >
                                    Add branch
                                </Button>
                            </Form.Item>
                        </>
                    )}
                </Form.List>
            </Form>
        </>
    )
}
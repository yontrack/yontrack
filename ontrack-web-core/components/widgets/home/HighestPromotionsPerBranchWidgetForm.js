import {Button, Form, Input, InputNumber, Segmented, Space, Switch} from "antd";
import {useContext} from "react";
import {FaPlus, FaTrash} from "react-icons/fa";
import SelectProjectBranch from "@components/branches/SelectProjectBranch";
import SelectProject from "@components/projects/SelectProject";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {
    DEFAULT_HIGHEST_PROMOTIONS_FORMAT,
    DEFAULT_HIGHEST_PROMOTIONS_SHOW_PROJECT,
    DEFAULT_HIGHEST_PROMOTIONS_SORT,
    DEFAULT_PATTERN_COUNT,
    entryKind,
    HIGHEST_PROMOTIONS_FORMATS,
    HIGHEST_PROMOTIONS_SORTS,
} from "@components/widgets/home/highestPromotionsPerBranch";

/**
 * An entry of the configuration, as the form edits it: a project and a branch together for a branch,
 * apart for a pattern, since the branch selector needs both and a pattern has no branch.
 */
const toFormEntry = entry => ({
    kind: entryKind(entry) ?? 'BRANCH',
    selectedBranch: {project: entry.project, branch: entry.branch},
    project: entry.project,
    pattern: entry.pattern,
    count: entry.count ?? DEFAULT_PATTERN_COUNT,
})

/**
 * Back to an entry of the configuration, carrying only what its kind needs: exactly one of `branch`
 * and `pattern`.
 */
const toConfigEntry = formEntry => formEntry?.kind === 'PATTERN' ?
    {
        project: formEntry.project,
        pattern: formEntry.pattern,
        // A cleared field gives null, which the widget's default does not catch
        count: formEntry.count ?? DEFAULT_PATTERN_COUNT,
    } :
    {
        project: formEntry?.selectedBranch?.project,
        branch: formEntry?.selectedBranch?.branch,
    }

export default function HighestPromotionsPerBranchWidgetForm({
                                                                 title,
                                                                 format = DEFAULT_HIGHEST_PROMOTIONS_FORMAT,
                                                                 showProject = DEFAULT_HIGHEST_PROMOTIONS_SHOW_PROJECT,
                                                                 sort = DEFAULT_HIGHEST_PROMOTIONS_SORT,
                                                                 branches = [],
                                                             }) {

    const {widgetEditionForm, onReceivingValuesHandler} = useContext(DashboardWidgetCellContext)

    onReceivingValuesHandler((values) => ({
        title: values.title,
        format: values.format ?? DEFAULT_HIGHEST_PROMOTIONS_FORMAT,
        showProject: values.showProject ?? DEFAULT_HIGHEST_PROMOTIONS_SHOW_PROJECT,
        sort: values.sort ?? DEFAULT_HIGHEST_PROMOTIONS_SORT,
        branches: (values.branches ?? []).map(toConfigEntry),
    }))

    // Exactly one of a branch and a pattern, the project being required for both
    const entryRule = name => ({getFieldValue}) => ({
        validator() {
            const formEntry = getFieldValue(['branches', name])
            const entry = toConfigEntry(formEntry)
            if (!entry.project) {
                return Promise.reject(new Error('The project is required'))
            } else if (!!entry.branch === !!entry.pattern) {
                return Promise.reject(new Error(
                    formEntry?.kind === 'PATTERN' ? 'The pattern is required' : 'The branch is required'
                ))
            } else {
                return Promise.resolve()
            }
        },
    })

    return (
        <>
            <Form
                layout="vertical"
                form={widgetEditionForm}
            >
                <Form.Item
                    name="title"
                    label="Title"
                    extra="If not provided, the title is Highest promotions per branch."
                    initialValue={title}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="format"
                    label="Format"
                    extra="The format shown until a viewer switches it in the widget, for their own browser only."
                    initialValue={format}
                >
                    <Segmented options={HIGHEST_PROMOTIONS_FORMATS}/>
                </Form.Item>
                <Form.Item
                    name="sort"
                    label="Sort"
                    initialValue={sort}
                >
                    <Segmented options={HIGHEST_PROMOTIONS_SORTS}/>
                </Form.Item>
                <Form.Item
                    name="showProject"
                    label="Show project name"
                    extra="Hide it only when all the branches come from one project: two branches of the same name would look alike."
                    initialValue={showProject}
                >
                    <Switch/>
                </Form.Item>
                <Form.List name="branches" initialValue={branches.map(toFormEntry)}>
                    {(fields, {add, remove}) => (
                        <>
                            {fields.map(({key, name, ...restField}) => (
                                <Space
                                    key={key}
                                    className="ot-form-list-item"
                                    align="baseline"
                                    wrap
                                >
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'kind']}
                                        label={
                                            <Space>
                                                <FaTrash onClick={() => remove(name)}/>
                                                Entry
                                            </Space>
                                        }
                                        dependencies={[
                                            ['branches', name, 'selectedBranch'],
                                            ['branches', name, 'project'],
                                            ['branches', name, 'pattern'],
                                        ]}
                                        rules={[entryRule(name)]}
                                    >
                                        <Segmented options={[
                                            {label: 'Branch', value: 'BRANCH'},
                                            {label: 'Pattern', value: 'PATTERN'},
                                        ]}/>
                                    </Form.Item>
                                    <Form.Item noStyle dependencies={[['branches', name, 'kind']]}>
                                        {({getFieldValue}) =>
                                            getFieldValue(['branches', name, 'kind']) === 'PATTERN' ?
                                                <Space align="baseline" wrap>
                                                    <Form.Item
                                                        {...restField}
                                                        name={[name, 'project']}
                                                        label="Project"
                                                    >
                                                        <SelectProject/>
                                                    </Form.Item>
                                                    <Form.Item
                                                        {...restField}
                                                        name={[name, 'pattern']}
                                                        label="Pattern"
                                                        extra="Regular expression on the name of the branches"
                                                    >
                                                        <Input placeholder="release-.*"/>
                                                    </Form.Item>
                                                    <Form.Item
                                                        {...restField}
                                                        name={[name, 'count']}
                                                        label="Count"
                                                        extra="Most recently active first"
                                                    >
                                                        <InputNumber min={1} step={1}/>
                                                    </Form.Item>
                                                </Space> :
                                                <Form.Item
                                                    {...restField}
                                                    name={[name, 'selectedBranch']}
                                                    label="Branch"
                                                >
                                                    <SelectProjectBranch/>
                                                </Form.Item>
                                        }
                                    </Form.Item>
                                </Space>
                            ))}
                            <Form.Item>
                                <Button type="dashed"
                                        onClick={() => add({kind: 'BRANCH', count: DEFAULT_PATTERN_COUNT})} block
                                        icon={<FaPlus/>}
                                >
                                    Add branch or pattern
                                </Button>
                            </Form.Item>
                        </>
                    )}
                </Form.List>
            </Form>
        </>
    )
}

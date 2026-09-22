import {gql} from "graphql-request";
import {Checkbox, Empty, Form, Input, Space, Spin} from "antd";
import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {useQuery} from "@components/services/GraphQL";
import LabelChip, {labelDisplay} from "@components/labels/LabelChip";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";

/**
 * The list of labels, as checkboxes rendered as chips, driven by the `labelIds` field of
 * the form.
 *
 * It is a controlled component of its own rather than an Ant Design `Checkbox.Group`,
 * because the group prunes from its `onChange` every value whose checkbox is not mounted -
 * so ticking a label after filtering the list would silently drop the labels the filter
 * hides. Here the value is the whole selection, whatever is being displayed.
 */
function LabelCheckboxes({labels, loading, value, onChange}) {

    const selected = value ?? []

    const toggle = (id, checked) => {
        const next = checked ? [...selected, id] : selected.filter(it => it !== id)
        onChange?.(next)
    }

    if (loading) return <Spin/>

    if (labels.length === 0) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No label"/>
    }

    return (
        <Space orientation="vertical" size={8}>
            {
                labels.map(label => {
                    const id = Number(label.id)
                    return (
                        <Checkbox
                            key={id}
                            checked={selected.includes(id)}
                            data-testid={`label-check-${labelDisplay(label)}`}
                            onChange={event => toggle(id, event.target.checked)}
                        >
                            <LabelChip label={label} link={false}/>
                        </Checkbox>
                    )
                })
            }
        </Space>
    )
}

const setProjectLabelsQuery = gql`
    mutation SetProjectLabels($projectId: Int!, $labelIds: [Int!]!) {
        setProjectLabels(input: {
            projectId: $projectId,
            labelIds: $labelIds,
        }) {
            errors {
                message
            }
        }
    }
`

/**
 * Dialog assigning labels to a project. It is started with `{project}`, whose `labels` are
 * the initial selection, and saves the whole selection through `setProjectLabels`.
 */
export const useProjectLabelsDialog = ({onSuccess}) => {
    return useFormDialog({
        onSuccess,
        init: (form, {project}) => {
            form.setFieldsValue({
                filter: '',
                labelIds: (project.labels ?? []).map(label => Number(label.id)),
            })
        },
        prepareValues: (values, {project}) => ({
            projectId: Number(project.id),
            labelIds: (values.labelIds ?? []).map(Number),
        }),
        query: setProjectLabelsQuery,
        userNode: 'setProjectLabels',
    })
}

export default function ProjectLabelsDialog({dialog}) {

    // All the labels, loaded only when the dialog is actually open - and again on each
    // opening, so that a label created meanwhile is there
    const {data: labels, loading} = useQuery(
        gql`
            query LabelsForAssignment {
                labels {
                    ...labelFragment
                }
            }

            ${gqlLabelFragment}
        `,
        {
            initialData: [],
            condition: dialog.open,
            dataFn: data => data.labels,
        }
    )

    // The filter is a field of the form, so that it is cleared with the rest of it when the
    // dialog is closed and does not survive into the next project it is opened for
    const filter = Form.useWatch('filter', dialog.form)

    // There are few labels and they are all loaded at once, so the filter is a plain
    // computation on the loaded list, never a state to keep in sync
    const text = (filter ?? '').trim().toLowerCase()
    const filteredLabels = (labels ?? []).filter(label => !text ||
        label.name.toLowerCase().includes(text) ||
        (label.category ?? '').toLowerCase().includes(text)
    )

    return (
        <FormDialog
            dialog={dialog}
            id="project-labels-dialog"
            height="400px"
            header={
                <Form.Item name="filter" noStyle>
                    <Input
                        placeholder="Filter the labels"
                        allowClear
                    />
                </Form.Item>
            }
        >
            <Form.Item name="labelIds">
                <LabelCheckboxes labels={filteredLabels} loading={loading}/>
            </Form.Item>
        </FormDialog>
    )
}

import {gql} from "graphql-request";
import {Form, Input, Space, Table, Typography} from "antd";
import {useState} from "react";
import Link from "next/link";
import {useQuery} from "@components/services/GraphQL";
import FilterForm from "@components/common/table/FilterForm";
import LabelChip from "@components/labels/LabelChip";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";
import {projectLabelUri} from "@components/common/Links";
import LabelUpdateCommand from "@components/core/config/LabelUpdateCommand";
import LabelDeleteCommand from "@components/core/config/LabelDeleteCommand";

export default function LabelsTable({refreshState, refresh}) {

    const [filterFormData, setFilterFormData] = useState({text: ''})

    const {data, loading} = useQuery(
        gql`
            query Labels {
                labels {
                    ...labelFragment
                    projectCount
                }
            }

            ${gqlLabelFragment}
        `,
        {
            deps: [refreshState],
            dataFn: data => data.labels,
        }
    )

    // The filter is one text, matched against the category and the name. There are few labels and
    // they are all loaded at once, so this is a plain computation on the loaded list rather than
    // another query - and never a state to keep in sync.
    const text = filterFormData.text?.trim()?.toLowerCase()
    const labels = (data ?? []).filter(label => !text ||
        label.name.toLowerCase().includes(text) ||
        (label.category ?? '').toLowerCase().includes(text)
    )

    return (
        <>
            <FilterForm
                setFilterFormData={setFilterFormData}
                filterForm={[
                    <Form.Item
                        key="text"
                        name="text"
                        label="Category or name"
                    >
                        <Input style={{width: "15em"}}/>
                    </Form.Item>
                ]}
            />
            <Table
                id="labels"
                data-testid="labels"
                loading={loading}
                dataSource={labels}
                pagination={false}
                rowKey="id"
            >
                <Table.Column
                    key="label"
                    title="Label"
                    render={(_, label) => <LabelChip label={label} link={false}/>}
                />
                <Table.Column
                    key="category"
                    title="Category"
                    render={(_, label) => <Typography.Text>{label.category}</Typography.Text>}
                />
                <Table.Column
                    key="name"
                    title="Name"
                    render={(_, label) => <Typography.Text>{label.name}</Typography.Text>}
                />
                <Table.Column
                    key="description"
                    title="Description"
                    render={(_, label) => <Typography.Text type="secondary">{label.description}</Typography.Text>}
                />
                <Table.Column
                    key="projectCount"
                    title="Projects"
                    render={(_, label) =>
                        <Link href={projectLabelUri(label)}>{label.projectCount}</Link>
                    }
                />
                <Table.Column
                    key="actions"
                    title="Actions"
                    render={(_, label) =>
                        <Space>
                            <LabelUpdateCommand label={label} onChange={refresh}/>
                            <LabelDeleteCommand label={label} onChange={refresh}/>
                        </Space>
                    }
                />
            </Table>
        </>
    )
}

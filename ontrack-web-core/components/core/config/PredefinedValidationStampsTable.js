import {useQuery} from "@components/services/useQuery";
import {gql} from "graphql-request";
import {Form, Input, Space, Table, Typography} from "antd";
import {useReloadState} from "@components/common/StateUtils";
import PredefinedValidationStampImage from "@components/core/config/PredefinedValidationStampImage";
import ValidationDataType from "@components/framework/validation-data-type/ValidationDataType";
import FilterForm from "@components/common/table/FilterForm";
import {useState} from "react";
import PredefinedValidationStampUpdateCommand from "@components/core/config/PredefinedValidationStampUpdateCommand";
import PredefinedValidationStampDeleteCommand from "@components/core/config/PredefinedValidationStampDeleteCommand";
import PredefinedValidationStampChangeImageCommand
    from "@components/core/config/PredefinedValidationStampChangeImageCommand";

export default function PredefinedValidationStampsTable({reloadState}) {

    const [changed, onChange] = useReloadState()

    const [filterFormData, setFilterFormData] = useState({
        name: ''
    })

    const {data, loading} = useQuery(
        gql`
            query PredefinedValidationStamps($name: String = null) {
                predefinedValidationStamps(name: $name) {
                    key: id
                    id
                    name
                    description
                    isImage
                    dataType {
                        descriptor {
                            id
                            displayName
                        }
                        config
                    }
                }
            }
        `,
        {
            variables: filterFormData,
            deps: [filterFormData, reloadState, changed]
        }
    )

    return (
        <>
            <FilterForm
                setFilterFormData={setFilterFormData}
                filterForm={[
                    <Form.Item
                        key="name"
                        name="name"
                        label="Name"
                    >
                        <Input style={{width: "15em"}}/>
                    </Form.Item>
                ]}
            />
            <Table
                loading={loading}
                dataSource={data?.predefinedValidationStamps}
                pagination={false}
            >
                <Table.Column
                    key="name"
                    title="Name"
                    render={(_, record) => <Space>
                        <PredefinedValidationStampImage
                            predefinedValidationStamp={record}
                        />
                        <Typography.Text>{record.name}</Typography.Text>
                    </Space>}
                />
                <Table.Column
                    key="description"
                    title="Description"
                    render={(_, record) => <Typography.Text
                        type="secondary">{record.description}</Typography.Text>}
                />
                <Table.Column
                    key="dataType"
                    title="Data type"
                    render={(_, record) =>
                        <>
                            {
                                record.dataType && <Space orientation="vertical">
                                    <Typography.Text strong>{record.dataType.descriptor.displayName}</Typography.Text>
                                    <ValidationDataType dataType={record.dataType}/>
                                </Space>
                            }
                            {
                                !record.dataType &&
                                <Typography.Text type="secondary">None</Typography.Text>
                            }
                        </>
                    }
                />
                <Table.Column
                    key="actions"
                    title="Actions"
                    render={(_, record) =>
                        <Space>
                            <PredefinedValidationStampUpdateCommand pvs={record} onChange={onChange}/>
                            <PredefinedValidationStampChangeImageCommand id={record.id} onChange={onChange}/>
                            <PredefinedValidationStampDeleteCommand pvs={record} onChange={onChange}/>
                        </Space>
                    }
                />
            </Table>
        </>
    )

}
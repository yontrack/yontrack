import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";
import {Space, Typography} from "antd";
import Table from "@components/common/table/Table";
import Link from "next/link";
import {FaDownload} from "react-icons/fa";

export default function JsonSchemasTable() {

    const {data, loading} = useQuery(
        gql`
            query JsonSchemas {
                jsonSchemaDefinitions {
                    key
                    id
                    title
                    description
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.jsonSchemaDefinitions,
        }
    )

    return (
        <>
            <Table
                dataSource={data}
                loading={loading}
                pagination={false}
            >
                <Table.Column
                    key="key"
                    title="Key"
                    dataIndex="key"
                />
                <Table.Column
                    key="id"
                    title="ID"
                    dataIndex="id"
                    render={(value, definition) =>
                        <Space>
                            <Typography.Text code>{value}</Typography.Text>
                            <Link
                                href={`/api/protected/downloads/ref/schema/json/${definition.key}`}
                                title={`Downloads the JSON schema for ${definition.title}`}
                            >
                                <FaDownload/>
                            </Link>
                        </Space>
                    }
                />
                <Table.Column
                    key="title"
                    title="Title"
                    dataIndex="title"
                />
                <Table.Column
                    key="description"
                    title="Description"
                    dataIndex="description"
                />
            </Table>
        </>
    )
}
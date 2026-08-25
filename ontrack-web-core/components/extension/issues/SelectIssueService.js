import {Select, Space, Typography} from "antd";
import {gql} from "graphql-request";
import {useQuery} from "@components/services/useQuery";
import InlineError from "@components/common/InlineError";

export default function SelectIssueService({id, value, onChange, self}) {

    const {data: options, loading, error} = useQuery(
        gql`
            query GetIssueServicesConfigurations {
                issueServiceConfigurations {
                    name
                    id
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => {
                const options = data.issueServiceConfigurations.map(({name, id}) => ({
                    value: id,
                    label: <Space>
                        <Typography.Text>{name}</Typography.Text>
                        <Typography.Text type="secondary">[{id}]</Typography.Text>
                    </Space>,
                }))
                if (self) {
                    options.push({
                        value: "self",
                        label: <Space>
                            <Typography.Text>{self}</Typography.Text>
                            <Typography.Text type="secondary">[self]</Typography.Text>
                        </Space>,
                    })
                }
                return options
            },
            deps: [self]
        }
    )

    if (error) {
        return <InlineError message={error}/>
    }

    return (
        <>
            <Select
                id={id}
                options={options}
                loading={loading}
                value={value}
                onChange={onChange}
                allowClear={true}
            />
        </>
    )
}
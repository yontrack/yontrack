import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

export default function SelectWebhookAuthenticator({id, value, onChange, onSelectedWebhookAuthenticator}) {
    const {loading, finished, data: options} = useQuery(
        gql`
            query SelectWebhookAuthenticator {
                webhookAuthenticators {
                    value: type
                    label: displayName
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.webhookAuthenticators,
        }
    )

    const onLocalChange = (value) => {
        if (onChange) onChange(value)
        if (onSelectedWebhookAuthenticator) onSelectedWebhookAuthenticator(value)
    }

    return (
        <>
            <Select
                id={id}
                options={options}
                loading={loading || !finished}
                value={value}
                onChange={onLocalChange}
                allowClear={true}
            />
        </>
    )
}
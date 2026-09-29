import {Space, Typography} from "antd";
import {gql} from "graphql-request";
import {restPredefinedValidationStampImageUri} from "@components/common/Links";
import {useQuery} from "@components/services/GraphQL";
import ProxyImage from "@components/common/ProxyImage";

export default function PredefinedValidationStampImageByName({name, displayName = true, size = 24}) {

    const {data: pvs} = useQuery(
        gql`
            query PredefinedValidationStamp($name: String!) {
                predefinedValidationStampByName(name: $name) {
                    id
                    isImage
                }
            }
        `,
        {
            variables: {name},
            deps: [name],
            dataFn: data => data.predefinedValidationStampByName,
        }
    )

    const image = pvs && pvs.isImage ?
        <ProxyImage
            restUri={restPredefinedValidationStampImageUri(pvs)}
            alt={`Predefined validation stamp ${name}`}
            width={size}
            height={size}
        /> :
        ''

    return (
        <Space size={8}>
            {image}
            {displayName && <Typography.Text>{name}</Typography.Text>}
        </Space>
    )
}

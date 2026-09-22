import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import LoadingContainer from "@components/common/LoadingContainer";
import {Card, Col, Row} from "antd";
import ItemList from "@components/common/ItemList";

export default function UserProfileGroups() {
    const {data, loading} = useQuery(
        gql`
            query UserProfileGroups {
                user {
                    assignedGroups {
                        name
                    }
                    mappedGroups {
                        name
                    }
                    idpGroups
                }
            }
        `,
        {
            initialData: {
                assignedGroups: [],
                mappedGroups: [],
                idpGroups: [],
            },
            dataFn: data => data.user,
        }
    )

    return (
        <>
            <LoadingContainer loading={loading}>
                <Row gutter={16}>
                    <Col span={8}>
                        <Card size="small" title="Assigned groups" variant="borderless">
                            <ItemList data-testid="assigned-groups">
                                {
                                    data.assignedGroups.map(item =>
                                        <ItemList.Item key={item.name} title={item.name}/>
                                    )
                                }
                            </ItemList>
                        </Card>
                    </Col>
                    <Col span={8}>
                        <Card size="small" title="Mapped groups" variant="borderless">
                            <ItemList data-testid="mapped-groups">
                                {
                                    data.mappedGroups.map(item =>
                                        <ItemList.Item key={item.name} title={item.name}/>
                                    )
                                }
                            </ItemList>
                        </Card>
                    </Col>
                    <Col span={8}>
                        <Card size="small" title="IdP groups" variant="borderless">
                            <ItemList data-testid="idp-groups">
                                {
                                    data.idpGroups.map(item =>
                                        <ItemList.Item key={item} title={item}/>
                                    )
                                }
                            </ItemList>
                        </Card>
                    </Col>
                </Row>
            </LoadingContainer>
        </>
    )
}
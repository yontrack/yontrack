import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Card, Space, Typography} from "antd";
import BuildLink from "@components/builds/BuildLink";
import PromotionRuns from "@components/promotionRuns/PromotionRuns";
import {isAuthorized} from "@components/common/authorizations";
import SlotPipelineCreateButton from "@components/extension/environments/SlotPipelineCreateButton";
import {gqlSlotPipelineBuildData} from "@components/extension/environments/EnvironmentGraphQL";

export default function SlotEligibleBuild({slot, onStart}) {
    const {data: loadedSlot, loading} = useQuery(
        gql`
            query SlotEligibleBuild($id: String!) {
                slotById(id: $id) {
                    authorizations {
                        name
                        action
                        authorized
                    }
                    eligibleBuild {
                        ...SlotPipelineBuildData
                    }
                }
            }
            ${gqlSlotPipelineBuildData}
        `,
        {
            variables: {id: slot?.id},
            deps: [slot],
            condition: !!slot,
            dataFn: data => data.slotById,
        }
    )
    const build = loadedSlot?.eligibleBuild

    return (
        <>
            <Card loading={loading} size="small" hoverable={true}>
                {
                    build &&
                    <Space>
                        <BuildLink build={build}/>
                        <PromotionRuns promotionRuns={build.promotionRuns}/>
                        <Typography.Text>is eligible</Typography.Text>
                        {
                            isAuthorized(loadedSlot, "pipeline", "create") &&
                            <SlotPipelineCreateButton
                                slot={slot}
                                build={build}
                                onStart={onStart}
                            />
                        }
                    </Space>
                }
                {
                    !build && <Typography.Text type="secondary">No eligible build</Typography.Text>
                }
            </Card>
        </>
    )
}
import {gql} from "graphql-request";
import {gqlSlotPipelineBuildData} from "@components/extension/environments/EnvironmentGraphQL";
import StandardTable from "@components/common/table/StandardTable";
import {Space} from "antd";
import BuildLink from "@components/builds/BuildLink";
import PromotionRuns from "@components/promotionRuns/PromotionRuns";
import {isAuthorized} from "@components/common/authorizations";
import {Button} from "antd";
import {FaPlay} from "react-icons/fa";
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog";

export default function SlotEligibleBuildsTable({slot, onChange, showEligibleBuilds = false}) {

    /*
     * The one way to start a deployment (#1797). `SlotPipelineCreateButton` used to start it from
     * here behind a `Popconfirm` warning that "all currently active deployments" would be cancelled
     * without saying which, or what was in them; the dialog names the deployment it would cancel.
     */
    const deployDialog = useDeployDialog({onSuccess: onChange})

    return (
        <>
            <StandardTable
                query={
                    gql`
                        query SlotEligibleBuilds(
                            $id: String!,
                            $deployableOnly: Boolean!,
                            $offset: Int! = 0,
                            $size: Int! = 5,
                        ) {
                            slotById(id: $id) {
                                eligibleBuilds(offset: $offset, size: $size, deployable: $deployableOnly) {
                                    pageInfo {
                                        nextPage {
                                            offset
                                            size  
                                        }                                    
                                    }
                                    pageItems {
                                        ...SlotPipelineBuildData
                                    }
                                }
                            }
                        }
                        ${gqlSlotPipelineBuildData}
                    `
                }
                variables={{id: slot.id, deployableOnly: !showEligibleBuilds}}
                queryNode={data => data.slotById.eligibleBuilds}
                filter={{}}
                columns={[
                    {
                        key: 'build',
                        title: 'Build',
                        render: (_, build) => <Space>
                            <BuildLink build={build}/>
                            <PromotionRuns promotionRuns={build.promotionRuns}/>
                            {
                                isAuthorized(slot, "pipeline", "create") &&
                                <Button
                                    icon={<FaPlay color="green"/>}
                                    title="Deploy this build into this slot"
                                    data-testid={`slot-eligible-deploy-${build.id}`}
                                    onClick={() => deployDialog.start({slot, build})}
                                />
                            }
                        </Space>
                    }
                ]}
            />
            <DeployDialog dialog={deployDialog}/>
        </>
    )
}
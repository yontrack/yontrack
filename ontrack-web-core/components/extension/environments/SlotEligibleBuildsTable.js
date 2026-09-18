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

/**
 * @param {function} onDeploy Where Deploy goes when the caller already owns a deploy dialog. The
 *   slot page does: its header block offers Deploy too, and a second mounted `DeployDialog` would
 *   put a second copy of every one of its test ids - and of its buttons - in the same document
 *   (#1793). Left out, the table mounts and owns one, which is what a standalone rendering wants.
 */
export default function SlotEligibleBuildsTable({slot, onChange, onDeploy, showEligibleBuilds = false}) {

    /*
     * The one way to start a deployment (#1797). `SlotPipelineCreateButton` used to start it from
     * here behind a `Popconfirm` warning that "all currently active deployments" would be cancelled
     * without saying which, or what was in them; the dialog names the deployment it would cancel.
     */
    const ownDialog = useDeployDialog({onSuccess: onChange})
    const deploy = onDeploy ?? ((slot, build) => ownDialog.start({slot, build}))

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
                                    onClick={() => deploy(slot, build)}
                                />
                            }
                        </Space>
                    }
                ]}
            />
            {!onDeploy && <DeployDialog dialog={ownDialog}/>}
        </>
    )
}
import {usePromotionLevelBranch} from "@components/promotionLevels/UsePromotionLevelBranch";
import LoadingContainer from "@components/common/LoadingContainer";
import {Space} from "antd";
import PromotionLevelByName from "@components/promotionLevels/PromotionLevelByName";

export default function Display({property, entityId}) {
    const {branch, loading} = usePromotionLevelBranch({promotionLevelId: entityId})
    return (
        <>
            <LoadingContainer loading={loading}>
                {
                    branch?.project?.name && <Space>
                        {
                            property.value.dependencies.map(plName => <>
                                <PromotionLevelByName
                                    projectName={branch.project.name}
                                    branchName={branch.name}
                                    name={plName}
                                />
                            </>)
                        }
                    </Space>
                }
            </LoadingContainer>
        </>
    )
}
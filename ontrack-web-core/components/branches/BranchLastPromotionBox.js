import {Space} from "antd";
import PromotionRunBox from "@components/promotionRuns/PromotionRunBox";
import LatestBuildBox from "@components/builds/LatestBuildBox";
import NoLatestBuildBox from "@components/builds/NoLatestBuildBox";
import BranchLink from "@components/branches/BranchLink";

export default function BranchLastPromotionBox({branch}) {

    const lastPromotionLevel = branch.promotionLevels ? branch.promotionLevels[branch.promotionLevels.length - 1] : undefined
    const latestBuild = branch.latestBuild ? branch.latestBuild[0] : undefined

    return (
        <>
            <Space orientation="vertical" size={4}>
                <BranchLink branch={branch}/>
                {
                    lastPromotionLevel ?
                        <PromotionRunBox promotionLevel={lastPromotionLevel}></PromotionRunBox> : undefined
                }
                {
                    !lastPromotionLevel && latestBuild ? <LatestBuildBox build={latestBuild}/> : undefined
                }
                {
                    !lastPromotionLevel && !latestBuild ? <NoLatestBuildBox/> : undefined
                }
            </Space>
        </>
    )
}
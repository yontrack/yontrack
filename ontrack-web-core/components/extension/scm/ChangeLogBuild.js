import GridCell from "@components/grid/GridCell";
import BranchLink from "@components/branches/BranchLink";
import {Space, Typography} from "antd";
import BuildLink from "@components/builds/BuildLink";
import Timestamp from "@components/common/Timestamp";
import PromotionRun from "@components/promotionRuns/PromotionRun";

export default function ChangeLogBuild({id, title, loading, build}) {
    return (
        <>
            <GridCell id={id} title={title} loading={loading} padding={true}>
                <Space direction="vertical">
                    <Space>
                        <BranchLink branch={build.branch}/>
                        <Typography.Text>/</Typography.Text>
                        <BuildLink build={build}/>
                        <Timestamp value={build.creation.time} prefix="Created at"/>
                    </Space>
                    <Space size={8}>
                        {
                            build.promotionRuns.map(promotionRun =>
                                <PromotionRun
                                    key={promotionRun.id}
                                    promotionRun={promotionRun}
                                    size={24}
                                />
                            )
                        }
                    </Space>
                </Space>
            </GridCell>
        </>
    )
}
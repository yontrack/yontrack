import {Space, Tag, Typography} from "antd";
import ProjectLinkByName from "@components/projects/ProjectLinkByName";

export default function OnPromotionValidationDataType({project, qualifier, promotion}) {
    return (
        <Space wrap>
            <Typography.Text>When the linked build of</Typography.Text>
            <ProjectLinkByName name={project}/>
            {
                qualifier &&
                <Tag data-testid="on-promotion-qualifier">{qualifier}</Tag>
            }
            <Typography.Text>reaches</Typography.Text>
            <Typography.Text strong data-testid="on-promotion-promotion">{promotion}</Typography.Text>
        </Space>
    )
}

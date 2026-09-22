import {Space} from "antd";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import CheckpointArrival from "@components/branches/views/deliverymap/checkpoints/CheckpointArrival";

/**
 * A promotion level on the delivery map.
 *
 * It shows no status beside the build: a promotion level is arrived at by being promoted, and
 * arriving IS the outcome. There is no such thing as a build which is promoted and failed.
 *
 * @param checkpoint The checkpoint to draw
 */
export default function PromotionLevelCheckpoint({checkpoint}) {

    const promotionLevel = {
        id: checkpoint.data?.promotionLevelId,
        name: checkpoint.name,
        image: checkpoint.data?.image,
        description: checkpoint.description,
    }

    return (
        <Space orientation="vertical" size={0}>
            <PromotionLevelLink promotionLevel={promotionLevel}/>
            <CheckpointArrival arrival={checkpoint.arrival} nothingText="Never promoted"/>
        </Space>
    )
}

import {Space, Typography} from "antd";
import CheckpointArrival from "@components/branches/views/deliverymap/checkpoints/CheckpointArrival";

/**
 * A checkpoint of a kind this frontend does not know.
 *
 * Checkpoint kinds are open on purpose - an extension contributes its own - so a frontend which is
 * older than the backend it talks to, or which has not been taught a kind yet, has to draw SOMETHING
 * rather than break the map around it. The name and the arrival are on every checkpoint whatever its
 * kind, which is exactly enough to keep the node readable.
 *
 * @param checkpoint The checkpoint to draw
 */
export default function UnknownCheckpoint({checkpoint}) {
    return (
        <Space orientation="vertical" size={0}>
            <Typography.Text strong>{checkpoint.name}</Typography.Text>
            <CheckpointArrival arrival={checkpoint.arrival}/>
        </Space>
    )
}

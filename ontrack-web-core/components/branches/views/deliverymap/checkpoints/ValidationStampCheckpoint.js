import {Space} from "antd";
import ValidationChip from "@components/primitives/ValidationChip";
import {validationStampUri} from "@components/common/Links";
import CheckpointArrival from "@components/branches/views/deliverymap/checkpoints/CheckpointArrival";

/**
 * A validation stamp on the delivery map.
 *
 * This is the one kind where arriving and succeeding come apart, so the status of the run is drawn
 * beside the stamp rather than left out. `ValidationChip` is that pairing everywhere else in the
 * product, and a stamp has to read identically wherever it appears, so the checkpoint arranges the
 * shared piece rather than restating it.
 *
 * @param checkpoint The checkpoint to draw
 */
export default function ValidationStampCheckpoint({checkpoint}) {

    const validationStamp = {
        id: checkpoint.data?.validationStampId,
        name: checkpoint.name,
        image: checkpoint.data?.image,
    }

    return (
        <Space orientation="vertical" size={0}>
            <ValidationChip
                validationStamp={validationStamp}
                statusID={checkpoint.arrival?.status}
                href={validationStampUri(validationStamp)}
            />
            <CheckpointArrival arrival={checkpoint.arrival} nothingText="Never run"/>
        </Space>
    )
}

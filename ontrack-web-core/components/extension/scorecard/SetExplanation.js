import {Space, Typography} from "antd";
import LabelChip from "@components/labels/LabelChip";
import {NO_ESTATE_MARKER_TEXT, NO_ESTATE_SET_TEXT} from "@components/extension/scorecard/scorecardModel";
import {estateMarkerDescription} from "@components/extension/scorecard/estates/estateModel";

/**
 * What a set of readings is: the project on its own, or an estate with its description, the marker
 * its delivery readings are read up to, and the labels which select the project in it.
 */
export default function SetExplanation({set, testId}) {
    const estate = set.estate
    return (
        <Space orientation="vertical" size={4} data-testid={testId}>
            <Typography.Text type="secondary">
                {
                    estate ?
                        (estate.description || `Read against the marker and the targets of the estate ${estate.name}.`) :
                        NO_ESTATE_SET_TEXT
                }
            </Typography.Text>
            <Typography.Text>
                <Typography.Text strong>Marker:</Typography.Text>{' '}
                {estate ? estateMarkerDescription(estate.marker) : NO_ESTATE_MARKER_TEXT}
            </Typography.Text>
            {
                estate?.labels?.length > 0 &&
                <Space size={4} wrap>
                    <Typography.Text strong>Labels:</Typography.Text>
                    {estate.labels.map(label => <LabelChip key={label.id} label={label}/>)}
                </Space>
            }
        </Space>
    )
}

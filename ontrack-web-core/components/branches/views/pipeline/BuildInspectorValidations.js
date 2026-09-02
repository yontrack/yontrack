import {Empty, Space, theme, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import ValidationChip from "@components/primitives/ValidationChip";
import TimestampText from "@components/common/TimestampText";
import {filterValidations, validationStatusId} from "@components/branches/views/pipeline/pipelineFacts";
import {NONE_STATUS_ID} from "@components/validationRuns/ValidationRunStatusConfig";

/**
 * The validations of the inspected build, one chip per stamp.
 *
 * Restricted to the SELECTED VALIDATION STAMP FILTER, the same one the timeline strip honours and
 * the same one the legacy builds table honours - it is stored per branch on the server and shared
 * through the context provider above both views, so a user's filter follows them when they switch.
 * There is deliberately no second, view-local "aggregate mode" answering the same question.
 *
 * @param build The inspected build, with its validations
 * @param selectedFilter The active validation stamp filter, if any
 */
export default function BuildInspectorValidations({build, selectedFilter}) {

    const {token} = theme.useToken()

    const validations = filterValidations(build?.validations, selectedFilter)

    return (
        <PageSection id="inspector-validations" title="Validations" padding={true}>
            <Space direction="vertical" size={token.marginXS} className="ot-line">
                {
                    validations.length === 0 &&
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="This build has no validation to show."
                    />
                }
                {
                    validations.map(validation => {
                        const statusId = validationStatusId(validation)
                        const lastStatus = validation.validationRuns?.[0]?.lastStatus
                        return (
                            <Space key={validation.validationStamp?.id} size={token.marginXS} wrap>
                                <ValidationChip
                                    id={`inspector-validation-${validation.validationStamp?.id}`}
                                    validationStamp={validation.validationStamp}
                                    statusID={statusId === NONE_STATUS_ID ? undefined : lastStatus?.statusID}
                                />
                                {
                                    lastStatus?.creation?.time &&
                                    <Typography.Text type="secondary" style={{fontSize: token.fontSizeSM}}>
                                        <TimestampText value={lastStatus.creation.time}/>
                                        {lastStatus.creation.user && ` by ${lastStatus.creation.user}`}
                                    </Typography.Text>
                                }
                            </Space>
                        )
                    })
                }
            </Space>
        </PageSection>
    )
}

import {Space, Spin, Tag, Typography} from "antd";
import Link from "next/link";
import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";
import ValidationStamp from "@components/validationStamps/ValidationStamp";
import PromotionLevel from "@components/promotionLevels/PromotionLevel";
import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import {promotionRunUri, validationRunUri} from "@components/common/Links";

/**
 * Conditions of an auto promotion, as `autoPromotionConditions` returns them.
 *
 * Two variants, which expect the matching shape of conditions:
 *
 * - **plain** (`PromotionLevel.autoPromotionConditions`) - the required validation stamps and
 *   promotion levels alone;
 * - **with build** (`withBuild`, `PromotionRun.autoPromotionConditions`) - each of them with its
 *   state for the run's build, a link to the run when there is one, and a summary line.
 *
 * These are the *current* conditions and states, not a record of what triggered a promotion.
 */
export default function AutoPromotionConditions({conditions, withBuild = false}) {
    const validationStamps = conditions.validationStamps ?? []
    const promotionLevels = conditions.promotionLevels ?? []
    return (
        <Space direction="vertical" data-testid="auto-promotion-conditions" style={{maxWidth: '32em'}}>
            {
                withBuild && <AutoPromotionSummary validationStamps={validationStamps} promotionLevels={promotionLevels}/>
            }
            {
                validationStamps.length > 0 && <>
                    <Typography.Text strong>Validations</Typography.Text>
                    <Space direction="vertical" size={4}>
                        {
                            validationStamps.map(item => withBuild ?
                                <ValidationStampCondition key={item.validationStamp.id} condition={item}/> :
                                <ValidationStampRow key={item.id} validationStamp={item}/>
                            )
                        }
                    </Space>
                </>
            }
            {
                promotionLevels.length > 0 && <>
                    <Typography.Text strong>Promotions</Typography.Text>
                    <Space direction="vertical" size={4}>
                        {
                            promotionLevels.map(item => withBuild ?
                                <PromotionLevelCondition key={item.promotionLevel.id} condition={item}/> :
                                <PromotionLevelRow key={item.id} promotionLevel={item}/>
                            )
                        }
                    </Space>
                </>
            }
            <AutoPromotionPatterns conditions={conditions}/>
            {
                conditions.autoRevoke &&
                <Typography.Text type="secondary">
                    Revoked when a prerequisite is no longer valid
                </Typography.Text>
            }
        </Space>
    )
}

function AutoPromotionSummary({validationStamps, promotionLevels}) {
    const parts = []
    if (validationStamps.length > 0) {
        const passed = validationStamps.filter(it => it.passed).length
        parts.push(`${passed}/${validationStamps.length} validations passed`)
    }
    if (promotionLevels.length > 0) {
        const granted = promotionLevels.filter(it => it.promotionRun).length
        parts.push(`${granted}/${promotionLevels.length} promotions granted`)
    }
    return parts.length > 0 ?
        <Typography.Text data-testid="auto-promotion-summary">{parts.join(' · ')}</Typography.Text> :
        null
}

function AutoPromotionPatterns({conditions}) {
    const parts = []
    if (conditions.include) {
        parts.push(<span key="include">Include <Typography.Text code>{conditions.include}</Typography.Text></span>)
    }
    if (conditions.exclude) {
        parts.push(<span key="exclude">Exclude <Typography.Text code>{conditions.exclude}</Typography.Text></span>)
    }
    return parts.length > 0 ?
        <Typography.Text type="secondary" data-testid="auto-promotion-patterns">
            <Space size={8} wrap>{parts}</Space>
        </Typography.Text> :
        null
}

function ValidationStampRow({validationStamp}) {
    return <span data-testid={`auto-promotion-vs-${validationStamp.name}`}>
        <ValidationStamp validationStamp={validationStamp} displayTooltip={false}/>
    </span>
}

function PromotionLevelRow({promotionLevel}) {
    return <span data-testid={`auto-promotion-pl-${promotionLevel.name}`}>
        <PromotionLevel promotionLevel={promotionLevel} displayText={true} displayTooltip={false}/>
    </span>
}

function ValidationStampCondition({condition}) {
    const {validationStamp, lastRun} = condition
    const stamp = <ValidationStamp
        validationStamp={validationStamp}
        displayLink={false}
        displayTooltip={false}
    />
    return <span data-testid={`auto-promotion-vs-${validationStamp.name}`}>
        {
            lastRun ?
                <Link href={validationRunUri(lastRun)} data-testid={`auto-promotion-vs-link-${validationStamp.name}`}>
                    <Space size={8}>
                        {stamp}
                        <ValidationRunStatus status={lastRun.lastStatus} tooltip={false}/>
                    </Space>
                </Link> :
                <Space size={8}>
                    <span style={{opacity: 0.5}}>{stamp}</span>
                    <Tag>Not run</Tag>
                </Space>
        }
    </span>
}

function PromotionLevelCondition({condition}) {
    const {promotionLevel, promotionRun} = condition
    const level = <PromotionLevel promotionLevel={promotionLevel} displayText={true} displayTooltip={false}/>
    return <span data-testid={`auto-promotion-pl-${promotionLevel.name}`}>
        {
            promotionRun ?
                <Link href={promotionRunUri(promotionRun)} data-testid={`auto-promotion-pl-link-${promotionLevel.name}`}>
                    {level}
                </Link> :
                <Space size={8}>
                    <span style={{opacity: 0.5}}>{level}</span>
                    <Tag>Not granted</Tag>
                </Space>
        }
    </span>
}

const entityFields = `
    id
    name
    image
`

const patternsFields = `
    include
    exclude
    autoRevoke
`

const plainConditionsFields = `
    ${patternsFields}
    validationStamps { ${entityFields} }
    promotionLevels { ${entityFields} }
`

/**
 * Loads and displays the conditions of the auto promotion of a promotion level.
 *
 * Meant to be mounted lazily (inside a popover): the query runs when the component mounts.
 */
export function PromotionLevelAutoPromotionConditions({promotionLevelId}) {
    const {data: conditions, loading, finished} = useQuery(
        gql`
            query PromotionLevelAutoPromotionConditions($id: Int!) {
                promotionLevel(id: $id) {
                    autoPromotionConditions {
                        ${plainConditionsFields}
                    }
                }
            }
        `,
        {
            variables: {id: Number(promotionLevelId)},
            deps: [promotionLevelId],
            dataFn: data => data.promotionLevel?.autoPromotionConditions,
        }
    )
    if (loading || !finished) {
        return <Spin size="small"/>
    }
    return conditions ?
        <AutoPromotionConditions conditions={conditions}/> :
        <Typography.Text type="secondary">No auto promotion condition</Typography.Text>
}

/**
 * Loads and displays the conditions of the auto promotion of the promotion level of a run, with their
 * state for the run's build, under a "Auto promotion conditions" title.
 *
 * Displays nothing when the promotion level has no auto promotion.
 *
 * Meant to be mounted lazily (inside a popover): the query runs when the component mounts.
 */
export function PromotionRunAutoPromotionConditions({promotionRunId}) {
    const {data: conditions, loading, finished} = useQuery(
        gql`
            query PromotionRunAutoPromotionConditions($id: Int!) {
                promotionRuns(id: $id) {
                    autoPromotionConditions {
                        ${patternsFields}
                        validationStamps {
                            validationStamp { ${entityFields} }
                            passed
                            lastRun {
                                id
                                lastStatus {
                                    statusID {
                                        id
                                        name
                                    }
                                }
                            }
                        }
                        promotionLevels {
                            promotionLevel { ${entityFields} }
                            promotionRun { id }
                        }
                    }
                }
            }
        `,
        {
            variables: {id: Number(promotionRunId)},
            deps: [promotionRunId],
            dataFn: data => data.promotionRuns?.[0]?.autoPromotionConditions,
        }
    )
    if (loading || !finished) {
        return <Spin size="small"/>
    }
    return conditions ?
        <Space direction="vertical" data-testid={`promotion-run-auto-promotion-conditions-${promotionRunId}`}>
            <Typography.Text strong>Auto promotion conditions</Typography.Text>
            <AutoPromotionConditions conditions={conditions} withBuild={true}/>
        </Space> :
        null
}

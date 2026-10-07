import {Space, Tag, Typography} from "antd";
import Link from "next/link";
import {FaCheckCircle} from "react-icons/fa";
import ValidationStamp from "@components/validationStamps/ValidationStamp";
import PromotionLevel from "@components/promotionLevels/PromotionLevel";
import {promotionLevelUri} from "@components/common/Links";
import {groupReadinessItems} from "@components/readiness/readinessModel";

/**
 * What a build still lacks to reach a promotion level or a slot, as `Build.readiness` says it.
 *
 * - `ready` - "Ready", and the `readyAction` the caller offers (promoting the build, for a level);
 * - otherwise, the missing items grouped by kind, each one with its message:
 *   a validation links to its stamp, a promotion shows the required level, a check gives its reason,
 *   and a level granted by a person says so.
 *
 * @param {Object} readiness The `Readiness` - `{ready, missing}`
 * @param {string} target `promotionLevel` or `slot` - what the readiness is for. A manual item of a
 *   promotion level is "Promoted by a person"; a manual item of a slot is an approval, whose
 *   message says so.
 * @param {?Array} validationStamps The stamps of the build's branch, `{id, name, image}`, to link
 *   the missing validations to
 * @param {?Array} promotionLevels The levels of the build's branch, `{id, name, image}`, to show the
 *   missing promotions with
 * @param {?ReactNode} readyAction Shown beside "Ready"
 */
export default function ReadinessContent({
                                             readiness,
                                             target,
                                             validationStamps = [],
                                             promotionLevels = [],
                                             readyAction,
                                         }) {

    if (readiness.ready) {
        return (
            <Space data-testid="readiness-ready" wrap>
                <Tag color="success" icon={<FaCheckCircle aria-hidden="true"/>}>
                    Ready
                </Tag>
                {readyAction}
            </Space>
        )
    }

    const groups = groupReadinessItems(readiness.missing)

    return (
        <Space orientation="vertical" size={8} style={{maxWidth: '32em'}}>
            {
                groups.map(group => (
                    <section
                        key={group.kind}
                        data-testid={`readiness-group-${group.kind}`}
                        aria-labelledby={`readiness-group-title-${group.kind}`}
                    >
                        <Typography.Text strong id={`readiness-group-title-${group.kind}`}>
                            {group.title}
                        </Typography.Text>
                        <ul style={{margin: 0, paddingInlineStart: '1.25em'}}>
                            {
                                group.items.map(item => (
                                    <li
                                        key={`${item.kind}-${item.name}`}
                                        data-testid={`readiness-item-${item.kind}-${item.name}`}
                                    >
                                        <ReadinessItem
                                            item={item}
                                            target={target}
                                            validationStamps={validationStamps}
                                            promotionLevels={promotionLevels}
                                        />
                                    </li>
                                ))
                            }
                        </ul>
                    </section>
                ))
            }
        </Space>
    )
}

function ReadinessItem({item, target, validationStamps, promotionLevels}) {
    switch (item.kind) {
        case 'VALIDATION': {
            const validationStamp = validationStamps.find(it => it.name === item.name)
            return (
                <Space size={8} wrap>
                    {
                        validationStamp ?
                            <ValidationStamp validationStamp={validationStamp} displayTooltip={false}/> :
                            <Typography.Text>{item.name}</Typography.Text>
                    }
                    <Typography.Text type="secondary">{item.message}</Typography.Text>
                </Space>
            )
        }
        case 'PROMOTION': {
            const promotionLevel = promotionLevels.find(it => it.name === item.name)
            return promotionLevel ?
                <Link href={promotionLevelUri(promotionLevel)}>
                    <PromotionLevel promotionLevel={promotionLevel} displayText={true} displayTooltip={false}/>
                </Link> :
                <Typography.Text>{item.name}</Typography.Text>
        }
        case 'MANUAL':
            return target === 'promotionLevel' ?
                <Typography.Text>Promoted by a person</Typography.Text> :
                <ItemWithName item={item}/>
        case 'ADMISSION_RULE':
            return <ItemWithName item={item}/>
        default:
            // CHECK, AGENT_POLICY, and whatever comes next: the message says it all
            return <Typography.Text>{item.message}</Typography.Text>
    }
}

/**
 * One run of text rather than two boxes side by side: a long message then wraps under its name
 * like a sentence, instead of dropping below it as a block.
 */
function ItemWithName({item}) {
    return (
        <Typography.Text>
            <Typography.Text strong>{item.name}</Typography.Text>
            {` — ${item.message}`}
        </Typography.Text>
    )
}

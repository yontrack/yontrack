import {useState} from "react";
import {Button, Popover, Spin, Typography} from "antd";
import {FaRegQuestionCircle} from "react-icons/fa";
import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";
import {useEventsForRefresh} from "@components/common/EventsContext";
import {BUILD_PROMOTED, BUILD_VALIDATED} from "@components/builds/buildEvents";
import ReadinessContent from "@components/readiness/ReadinessContent";

export const gqlBuildReadiness = gql`
    query BuildReadiness($buildId: Int!, $promotionLevel: String, $slotId: String) {
        build(id: $buildId) {
            id
            readiness(promotionLevel: $promotionLevel, slotId: $slotId) {
                ready
                missing {
                    kind
                    name
                    message
                }
            }
            branch {
                validationStamps {
                    id
                    name
                    image
                }
                promotionLevels {
                    id
                    name
                    image
                }
            }
        }
    }
`

/**
 * The page events after which an open popover asks again: a validation or a promotion of the build
 * is what changes its readiness.
 */
const refreshEvents = [BUILD_VALIDATED, BUILD_PROMOTED]

/**
 * "What's missing" for a build to reach a promotion level or a slot (#2023): an icon button, opening
 * a popover which lists what `Build.readiness` says is missing.
 *
 * The readiness is asked **when the popover opens**, and again each time it reopens, never when the
 * page loads - a build page has one such control per promotion level and per slot, and almost none
 * of them is ever opened. While it is open, a validation or a promotion of the build fired on the
 * page's event bus asks again.
 *
 * Exactly one of `promotionLevel` and `slot` is given.
 *
 * @param {Object} build The build, with its `id`
 * @param {?Object} promotionLevel The promotion level, with its `id` and `name`
 * @param {?Object} slot The slot, with its `id`
 * @param {string} label How the target is named, in the accessible name of the button and in the
 *   title of the popover
 * @param {?ReactNode} readyAction Offered beside "Ready" when nothing is missing
 * @param {?string} testId Test id of the button. The popover's content gets `${testId}-popover`.
 */
export default function WhatsMissing({build, promotionLevel, slot, label, readyAction, testId}) {

    const [open, setOpen] = useState(false)

    const refreshCount = useEventsForRefresh(refreshEvents)

    const {data, loading, error, finished} = useQuery(gqlBuildReadiness, {
        variables: {
            buildId: Number(build.id),
            promotionLevel: promotionLevel?.name ?? null,
            slotId: slot?.id ?? null,
        },
        condition: open,
        deps: [build.id, promotionLevel?.name, slot?.id, refreshCount],
        dataFn: data => data.build,
    })

    const title = `What's missing for ${label}`

    const onKeyDown = (e) => {
        if (e.key === 'Escape' && open) {
            setOpen(false)
        }
    }

    const body = () => {
        if (loading || !finished) {
            return <Spin size="small" aria-label="Loading"/>
        } else if (error) {
            return <Typography.Text type="danger">{error}</Typography.Text>
        } else if (data?.readiness) {
            return <ReadinessContent
                readiness={data.readiness}
                target={promotionLevel ? 'promotionLevel' : 'slot'}
                validationStamps={data.branch?.validationStamps}
                promotionLevels={data.branch?.promotionLevels}
                readyAction={readyAction}
            />
        } else {
            return null
        }
    }

    return (
        <Popover
            trigger="click"
            open={open}
            onOpenChange={setOpen}
            title={title}
            content={
                <div
                    role="dialog"
                    aria-label={title}
                    data-testid={testId ? `${testId}-popover` : undefined}
                    onKeyDown={onKeyDown}
                >
                    {/* Announces the new answer when a validation or a promotion makes it change */}
                    <div aria-live="polite" aria-busy={loading}>
                        {body()}
                    </div>
                </div>
            }
        >
            <Button
                type="text"
                size="small"
                icon={<FaRegQuestionCircle aria-hidden="true"/>}
                aria-label={title}
                title={title}
                aria-haspopup="dialog"
                aria-expanded={open}
                data-testid={testId}
                onKeyDown={onKeyDown}
            />
        </Popover>
    )
}

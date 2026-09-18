import {Badge, Space, Tooltip, Typography} from "antd"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import {
    buildLabel,
    compactAge,
    deployedBuild,
    inFlightDeployment,
    inFlightLabel,
    slotDisplayName,
    topPromotionRun,
} from "@components/extension/environments/shared/slotCellModel"

/**
 * One slot, in the space of a table cell.
 *
 * The unit of the matrix, of the project slot graph and of the environment widgets. It is a *pure*
 * component: everything it draws comes from the `slot` it is handed, and it asks the server for
 * nothing. That is what makes a matrix of a hundred of them a single query rather than a hundred -
 * `gqlSlotCellData` is the shape it reads, and the screen around it decides when to ask again.
 *
 * Four things share the one line, in this order of importance:
 *
 * 1. **What is deployed** - the build, its top promotion, how long it has been there. This is the
 *    question the matrix exists to answer, so it is never displaced by anything below.
 * 2. **What is in flight**, as an overlay beneath it rather than in its place: "→ 107 candidate"
 *    is what is *trying* to be there, and a reader who takes it for the deployed build has been
 *    misled.
 * 3. **Blocked** - a red dot, because a held-up deployment is the thing somebody has to act on.
 * 4. **Behind** - a muted badge, because it is a comparison with another slot rather than a fact
 *    about this one, and it must not shout louder than 3.
 *
 * A slot which has never been deployed says so. A project with *no* slot in an environment is not
 * this component's business at all: the matrix leaves that cell empty rather than drawing a dash,
 * because "there is no such slot" and "there is a slot and nothing is in it" are different answers.
 *
 * @param {Object} slot The slot, as `SlotCellData` describes it.
 * @param {function} onClick Called with the slot when the cell is activated - the matrix opens the
 *   drawer on it. When absent the cell is inert and not focusable.
 * @param {string} testId Overrides the default test id, for a caller drawing several cells of the
 *   same slot (the widgets do).
 */
export default function SlotCell({slot, onClick, testId}) {

    if (!slot) return null

    const build = deployedBuild(slot)
    const promotionRun = topPromotionRun(build)
    const inFlight = inFlightDeployment(slot)
    const overlay = inFlightLabel(slot)

    const id = testId ?? `slot-cell-${slot.id}`

    const activate = onClick ? () => onClick(slot) : undefined

    return (
        <div
            data-testid={id}
            data-blocked={slot.blocked ? 'yes' : 'no'}
            data-behind={slot.behind ? 'yes' : 'no'}
            className="ot-slot-cell"
            role={activate ? 'button' : undefined}
            tabIndex={activate ? 0 : undefined}
            aria-label={slotDisplayName(slot)}
            onClick={activate}
            onKeyDown={
                activate ?
                    (event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault()
                            activate()
                        }
                    } :
                    undefined
            }
            style={{cursor: activate ? 'pointer' : undefined}}
        >
            {/* What is deployed */}
            {
                build ?
                    <Space size={6} data-testid={`${id}-deployed`}>
                        <Typography.Text strong>{buildLabel(build)}</Typography.Text>
                        {
                            promotionRun &&
                            <PromotionLevelImage
                                promotionLevel={promotionRun.promotionLevel}
                                size={16}
                            />
                        }
                        <Typography.Text type="secondary" data-testid={`${id}-age`}>
                            {compactAge(slot.lastDeployedPipeline?.end ?? slot.lastDeployedPipeline?.start)}
                        </Typography.Text>
                        {
                            /*
                             * The blocked dot sits beside the deployed build rather than beside the
                             * overlay: a reader scanning the column for red finds it on the same
                             * line whether or not the slot happens to have something in flight.
                             */
                            slot.blocked &&
                            <Tooltip title="This deployment is waiting on a failing check.">
                                <Badge status="error" data-testid={`${id}-blocked`}/>
                            </Tooltip>
                        }
                    </Space> :
                    <Space size={6}>
                        <Typography.Text type="secondary" data-testid={`${id}-never-deployed`}>
                            Never deployed
                        </Typography.Text>
                        {
                            slot.blocked &&
                            <Tooltip title="This deployment is waiting on a failing check.">
                                <Badge status="error" data-testid={`${id}-blocked`}/>
                            </Tooltip>
                        }
                    </Space>
            }

            {/* What is in flight */}
            {
                inFlight &&
                <div>
                    <Typography.Text type="secondary" data-testid={`${id}-in-flight`}>
                        {overlay}
                    </Typography.Text>
                </div>
            }

            {/* Behind the slot before it */}
            {
                slot.behind &&
                <div>
                    <Tooltip title="An environment before this one holds a newer build.">
                        <Typography.Text type="warning" data-testid={`${id}-behind`}>
                            ⚠ behind
                        </Typography.Text>
                    </Tooltip>
                </div>
            }
        </div>
    )
}

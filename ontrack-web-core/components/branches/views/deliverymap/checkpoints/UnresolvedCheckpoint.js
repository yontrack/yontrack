import {Space, Typography} from "antd";
import {FaUnlink} from "react-icons/fa";

/**
 * What the map draws in place of something a configuration named and which does not exist.
 *
 * Both slot admission rules reference their target BY NAME, inside an opaque JSON column, with no
 * referential integrity behind them: a promotion admission rule can name a promotion level the
 * branch does not have, and an environment rule can name an environment this project has no slot in.
 * Until this checkpoint existed, nothing said so - the deployment answered "Promotion not existing"
 * at the moment somebody tried to deploy, and not before.
 *
 * Drawing nothing at all was the alternative, and it is the wrong one: the map would then quietly
 * agree with the broken configuration. This is arguably the strongest thing the delivery map does.
 *
 * It is deliberately NOT a link and names no build. There is no entity behind it to link to, and
 * nothing can ever arrive at something which does not exist; a "Never reached" line would suggest
 * the thing is real and merely unvisited.
 *
 * What this must never look like is a checkpoint left out because the user may not see it. Such a
 * checkpoint is removed together with its edges and is never drawn at all - see the contributor
 * seam. If the two looked alike, people would learn to read "unresolved" as "probably just
 * permissions", and the feature would stop being trustworthy for the case it exists to catch. Hence
 * the wording here, which says what is broken rather than what is missing.
 *
 * @param checkpoint The checkpoint to draw
 */
export default function UnresolvedCheckpoint({checkpoint}) {

    const {reference} = checkpoint.data ?? {}
    const {text, tooltip} = unresolvedWording(reference)

    return (
        <Space orientation="vertical" size={0}>
            <Space size={4}>
                <Typography.Text type="warning"><FaUnlink/></Typography.Text>
                {/* Quoted as code: it is a name copied out of a configuration, not an entity of
                    Yontrack, and every other checkpoint's name is a link to something real */}
                <Typography.Text code>{checkpoint.name}</Typography.Text>
            </Space>
            {/* Short enough to fit the width `checkpointTypes` reserves for the node: elk lays the
                map out against that width, and a node drawn wider covers whatever sits beside it.
                The full explanation is in the tooltip */}
            <Typography.Text type="warning" italic title={tooltip}>{text}</Typography.Text>
        </Space>
    )
}

/**
 * The half of every tooltip which must never drift between the cases below: whatever failed to
 * match, the reason is a broken configuration and never a permission.
 */
const NOT_HIDDEN = "The configuration is broken; this is not something being hidden from you."

/**
 * What to say about a name which matched nothing, by the kind of thing that was looked for.
 *
 * A kind this frontend does not know still gets an answer: the set of checkpoint kinds is open, so an
 * extension may one day contribute a rule referencing something the core has never heard of.
 *
 * @param reference Kind of checkpoint the configuration asked for
 */
function unresolvedWording(reference) {
    switch (reference) {
        case 'promotion-level':
            return {
                text: "No such promotion level",
                tooltip: "An admission rule requires this promotion, but this branch has no promotion level of " +
                    `that name. ${NOT_HIDDEN}`,
            }
        case 'slot':
            return {
                text: "No such slot in this project",
                tooltip: "An admission rule requires a deployment in this environment, but this project has no " +
                    `slot there. ${NOT_HIDDEN}`,
            }
        default:
            return {
                text: "Matches nothing",
                tooltip: `A configuration names this, and nothing matches it. ${NOT_HIDDEN}`,
            }
    }
}

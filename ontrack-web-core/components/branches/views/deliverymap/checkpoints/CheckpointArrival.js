import {Space, Tag, theme, Typography} from "antd";
import BuildLink from "@components/builds/BuildLink";
import TimestampText from "@components/common/TimestampText";

/**
 * The build a checkpoint names, on every kind of checkpoint.
 *
 * "Arriving" is what the three unlike kinds have in common, so it reads the same on all of them: the
 * build, when it got there, and how far behind the branch has left it. What became of it there is
 * drawn by the checkpoint itself, because only a validation stamp has anything to say about it.
 *
 * A checkpoint nothing has reached says so in words. Leaving the line out would make an unreached
 * checkpoint and a checkpoint whose build is still loading look alike.
 *
 * @param arrival The checkpoint's arrival, absent when nothing has reached it
 * @param nothingText What to say when nothing has reached this checkpoint
 */
export default function CheckpointArrival({arrival, nothingText = "Never reached"}) {

    const {token} = theme.useToken()

    if (!arrival) {
        return <Typography.Text type="secondary" italic>{nothingText}</Typography.Text>
    }

    return (
        <Space size={token.marginXXS}>
            <BuildLink build={arrival.build} displayTooltip={true}/>
            <Typography.Text type="secondary">
                <TimestampText value={arrival.time} relative={true}/>
            </Typography.Text>
            <CheckpointLag lag={arrival.lag}/>
        </Space>
    )
}

/**
 * How far behind the branch's head the checkpoint's build is.
 *
 * The marker each checkpoint carries INSTEAD of an edge back to the branch's latest build: an edge
 * from that build to every checkpoint would mean neither *unlocks* nor *requires*, and would fan out
 * across the whole map at once. Twenty checkpoints each stating their own lag are read one at a time.
 *
 * Nothing at all when the lag is unknown rather than zero - a slot naming another branch's build
 * (ADR 0009), and a branch with no head. A number there would read as a fact rather than as a
 * category error.
 *
 * No colour: on this map colour is never what carries a meaning, and "behind" is not a failure
 * anyway - it is the normal state of every checkpoint but the first.
 *
 * @param lag Builds of the branch more recent than this one, null when it cannot be counted
 */
function CheckpointLag({lag}) {

    if (lag === null || lag === undefined) return null

    const atHead = lag === 0

    return (
        <Tag
            variant="filled"
            data-testid="checkpoint-lag"
            title={
                atHead ?
                    "This is the branch's latest build" :
                    `${lag} more recent ${lag > 1 ? 'builds have' : 'build has'} not reached this checkpoint`
            }
        >
            {atHead ? "at head" : `${lag} behind`}
        </Tag>
    )
}

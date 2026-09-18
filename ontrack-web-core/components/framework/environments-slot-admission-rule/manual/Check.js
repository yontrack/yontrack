import {Typography} from "antd";
import TimestampText from "@components/common/TimestampText";

/**
 * A manual approval's account of itself: who answered, when, what they said, and which way.
 *
 * Until #1793 this said "Deployment must be approved manually" whatever had happened to it - a
 * restatement of the rule's own summary, which is drawn beside it anyway. The interesting facts
 * were in the deployment's stored rule data all along and reached no screen: an approval nobody can
 * attribute is not much of an approval, and a *rejection* looked exactly like never having been
 * asked.
 *
 * @param {Object} ruleConfig The configured rule: its message, and who may answer it.
 * @param {Object} ruleData What was answered - `{user, timestamp, data: {approval, message}}`.
 */
export default function ManualAdmissionRuleCheck({check, ruleConfig, ruleData}) {

    // No answer yet. The row already says "Blocking" and names the rule; who is allowed to answer is
    // the one thing it does not say, and it is what somebody reading a blocked deployment needs.
    if (!ruleData) {
        return <Typography.Text type="secondary" data-testid="manual-approval-pending">
            {approversSentence(ruleConfig)}
        </Typography.Text>
    }

    const approved = !!ruleData.data?.approval
    const message = ruleData.data?.message

    return (
        <Typography.Text type="secondary" data-testid="manual-approval-detail">
            {`${approved ? 'Approved' : 'Rejected'} by ${ruleData.user} `}
            <TimestampText value={ruleData.timestamp} relative={true}/>
            {message ? ` — ${message}` : ''}
        </Typography.Text>
    )
}

/**
 * Who may answer this rule, in words.
 *
 * Empty lists mean "anybody who can act on the deployment", which is what the backend does with
 * them - it only checks a list when there is one.
 */
const approversSentence = (ruleConfig) => {
    const users = ruleConfig?.users ?? []
    const groups = ruleConfig?.groups ?? []
    if (users.length === 0 && groups.length === 0) {
        return "Waiting for a manual approval."
    }
    const parts = []
    if (users.length > 0) parts.push(users.join(", "))
    if (groups.length > 0) parts.push(`group${groups.length > 1 ? 's' : ''} ${groups.join(", ")}`)
    return `Waiting for a manual approval from ${parts.join(" or ")}.`
}

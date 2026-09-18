import {Dynamic} from "@components/common/Dynamic";

/**
 * What an admission rule has to say about *this* deployment, beyond passing or failing.
 *
 * Each rule ships a `Check` component beside its `Form` and its `Summary`; until #1793 nothing
 * rendered any of them, and the one that had something to say - the manual approval, which knows
 * who approved and what they wrote - said it to nobody. "What's blocking" now draws this under a
 * rule row whenever the deployment carries an answer to that rule.
 *
 * The verdict icon and the rule's name are the row's business, not this component's: a second tick
 * under the first one would only say the same thing twice.
 *
 * @param {Object} check The rule's verdict, `{ok, reason}`.
 * @param {string} ruleId Which rule, which picks the component.
 * @param {Object} ruleConfig How the rule is configured on the slot.
 * @param {Object} ruleData What was answered to it - `{user, timestamp, data}` - or null.
 */
export default function SlotAdmissionRuleCheck({check, ruleId, ruleConfig, ruleData}) {
    return <Dynamic
        path={`framework/environments-slot-admission-rule/${ruleId}/Check`}
        props={{check, ruleConfig, ruleData}}
    />
}

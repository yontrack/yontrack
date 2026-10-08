import {Typography} from "antd";

/**
 * Help of the keywords of a subscription: what they match, and the keywords on the actor of the
 * event (#2031).
 */
export default function SubscriptionKeywordsHelp() {
    return (
        <div data-testid="subscription-keywords-help">
            Space-separated keywords, all of which must match the event, ignoring the case: the name of
            one of its entities (branch, promotion level, ...) or one of its values. On the actor of the
            event:
            <ul style={{margin: 0, paddingInlineStart: "1.5em"}}>
                <li><Typography.Text code>actor:agent</Typography.Text> - done by an agent</li>
                <li><Typography.Text code>actor:human</Typography.Text> - done by a person</li>
                <li>
                    <Typography.Text code>agent:&lt;slug&gt;[agent]</Typography.Text> or <Typography.Text
                    code>agent:&lt;slug&gt;</Typography.Text> - done by this agent
                </li>
            </ul>
        </div>
    )
}

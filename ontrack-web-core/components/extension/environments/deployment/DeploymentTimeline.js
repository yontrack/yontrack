import {Timeline, Typography} from "antd"
import TimestampText from "@components/common/TimestampText"
import {timelineEntries} from "@components/extension/environments/deployment/deploymentModel"

/**
 * The audit timeline: how this deployment got where it is, newest first.
 *
 * The side column of the page goes to this rather than to more checks, because it is the one thing
 * the old page did not show at all. `SlotPipeline.changes` has always recorded who moved the
 * deployment, when, and what they wrote when they overrode something; none of it reached a screen.
 * An override in particular is a decision somebody took on purpose, and a green tick with the
 * decision hidden inside it is the shape of an audit that cannot be done.
 *
 * @param {Object} deployment The deployment, with its `changes`.
 */
export default function DeploymentTimeline({deployment}) {

    const entries = timelineEntries(deployment)

    if (entries.length === 0) {
        return <Typography.Text type="secondary" data-testid="deployment-timeline-empty">
            Nothing has happened to this deployment yet.
        </Typography.Text>
    }

    return (
        <div data-testid="deployment-timeline">
        <Timeline
            items={entries.map(entry => ({
                key: entry.key,
                color: entry.type === 'STATUS'
                    ? (entry.status === 'CANCELLED' ? 'red' : entry.status === 'DONE' ? 'green' : 'blue')
                    : 'orange',
                children: (
                    <div data-testid={`deployment-timeline-${entry.key}`}>
                        <div>
                            <Typography.Text>{entry.title}</Typography.Text>
                            <Typography.Text type="secondary">{` — ${entry.user}`}</Typography.Text>
                        </div>
                        {
                            entry.message &&
                            <div>
                                <Typography.Text
                                    italic
                                    data-testid={`deployment-timeline-message-${entry.key}`}
                                >
                                    {entry.message}
                                </Typography.Text>
                            </div>
                        }
                        <div>
                            <Typography.Text type="secondary">
                                <TimestampText value={entry.timestamp} relative={true}/>
                            </Typography.Text>
                        </div>
                    </div>
                ),
            }))}
        />
        </div>
    )
}

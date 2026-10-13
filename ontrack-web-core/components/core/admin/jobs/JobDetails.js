import {Descriptions} from "antd";
import DurationMs from "@components/common/DurationMs";
import TimestampText from "@components/common/TimestampText";

export default function JobDetails({job}) {
    const items = []
    if (job.runningOn) {
        items.push(
            {
                key: 'runningOn',
                label: 'Running on',
                children: job.runningOn,
            },
            {
                key: 'runningSince',
                label: 'Running since',
                children: <TimestampText value={job.runningSince}/>,
            },
            {
                key: 'progress',
                label: 'Progress',
                children: job.progressText,
            },
        )
    }
    items.push(
        {
            key: 'runCount',
            label: 'Run count',
            children: job.runCount,
        },
        {
            key: 'lastDuration',
            label: 'Last duration',
            children: <DurationMs ms={job.lastRunDurationMs} displaySeconds={true} displaySecondsInTooltip={false}/>,
        },
        {
            key: 'lastErrorCount',
            label: 'Last error count',
            children: job.lastErrorCount,
        },
        {
            key: 'lastTimeoutCount',
            label: 'Last timeout count',
            children: job.lastTimeoutCount,
        },
        {
            key: 'lastRunDate',
            label: 'Last run',
            children: <TimestampText value={job.lastRunDate}/>,
        },
        {
            key: 'nextRunDate',
            label: 'Next run',
            children: <TimestampText value={job.nextRunDate}/>,
        },
    )
    if (job.lastError) {
        items.push({
            key: 'lastError',
            label: 'Last error',
            children: job.lastError,
        })
    }

    return (
        <>
            <Descriptions
                items={items}
            />
        </>
    )
}
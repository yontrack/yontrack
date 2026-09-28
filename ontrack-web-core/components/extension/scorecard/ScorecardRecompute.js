import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {Button} from "antd";
import {FaSync} from "react-icons/fa";
import {callGraphQL} from "@components/services/GraphQL";
import {isComputedAfter, latestComputedAt} from "@components/extension/scorecard/scorecardModel";

const POLL_INTERVAL_MS = 2000
const MAX_POLLS = 30

const gqlRecomputeProjectScorecard = gql`
    mutation RecomputeProjectScorecard($projectId: Int!) {
        recomputeProjectScorecard(input: {projectId: $projectId}) {
            errors {
                message
            }
        }
    }
`

/**
 * A recompute, queued as a job, never run inline: once `request` has queued it, the readings are
 * reloaded (`refresh`) every two seconds until one computed after the latest one known (`latest`)
 * shows up, for a minute at most.
 *
 * @param latest Time of the latest computation known, as the API gives it, `null` for none
 * @param refresh Reloads the readings, and so `latest`
 * @param request Queues the recompute; resolves to the message of its error, `null` for none
 * @return `{recompute, polling, error}`
 */
export const useRecomputePolling = ({latest, refresh, request}) => {

    // Latest computation known when the recompute was queued, and the reloads done since
    const [pending, setPending] = useState(null)
    const [error, setError] = useState(null)

    const done = !!pending && isComputedAfter(latest, pending.baseline)
    const polling = !!pending && !done && pending.polls < MAX_POLLS

    useEffect(() => {
        if (polling) {
            const timer = setTimeout(() => {
                setPending(it => it && {...it, polls: it.polls + 1})
                refresh()
            }, POLL_INTERVAL_MS)
            return () => clearTimeout(timer)
        }
        // `refresh` is a new function at every render: one reload per poll, not per render
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [polling, pending?.polls])

    const recompute = async () => {
        setError(null)
        try {
            const message = await request()
            if (message) {
                setError(message)
            } else {
                setPending({baseline: latest, polls: 0})
            }
        } catch (ex) {
            setError(ex.message)
        }
    }

    return {recompute, polling, error}
}

/**
 * Recompute of the scorecard of a project, polled until its new readings show up.
 *
 * @param projectId ID of the project
 * @param scorecard Scorecard as currently loaded
 * @param refresh Reloads the scorecard
 * @return `{recompute, polling, error}`
 */
export const useScorecardRecompute = ({projectId, scorecard, refresh}) =>
    useRecomputePolling({
        latest: latestComputedAt(scorecard),
        refresh,
        request: async () => {
            const data = await callGraphQL({query: gqlRecomputeProjectScorecard, variables: {projectId: Number(projectId)}})
            return data?.recomputeProjectScorecard?.errors?.[0]?.message ?? null
        },
    })

/**
 * The "Recompute" command of a scorecard, spinning while the new readings are awaited.
 */
export function ScorecardRecomputeButton({recompute, polling, size}) {
    return (
        <Button
            type="text"
            size={size}
            icon={<FaSync/>}
            loading={polling}
            onClick={recompute}
            data-testid="scorecard-recompute"
            title="Recomputes the readings of the project, in every set it is in, overwriting those of the day"
        >
            {polling ? 'Recomputing' : 'Recompute'}
        </Button>
    )
}

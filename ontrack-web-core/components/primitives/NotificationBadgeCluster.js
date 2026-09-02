// @ts-check

import {Space, Typography} from "antd"
import StatePill from "@components/primitives/StatePill"

/**
 * The count badges shown against an entity - typically sitting on a promotion
 * medal, but nothing here assumes a medal.
 *
 * THESE COUNT NOTIFICATION RECORDS, NOT WORKFLOWS. A workflow is one delivery
 * channel among several - mail, Slack and webhook are others - so a
 * workflow-scoped reading of these badges would show empty counts to any user
 * whose subscriptions deliver elsewhere. The design handoff for the pipeline
 * view calls these "workflow badges"; that name is deliberately not used here.
 * See `docs/adr/0002-promotion-run-badges-count-notifications.md`.
 *
 * Purely presentational: it is handed counts and renders them. Fetching lives in
 * `EntityNotificationsBadge`, so the cluster can be reused by any caller that
 * already has the numbers.
 */

/**
 * One bucket per pill, in the order they are read.
 *
 * Error first would be louder, but the buckets are laid out in lifecycle order -
 * done, doing, broken - so the row reads the same way every time regardless of
 * which buckets happen to be non-zero.
 *
 * `badgeColour` is the antd `Badge` colour for the same bucket, kept here rather
 * than in the caller so a bucket's colour is decided in exactly one place
 * whichever of the two presentations ends up drawing it.
 *
 * @type {ReadonlyArray<{key: 'success'|'running'|'error', state: string, badgeColour: string, pulse: boolean, title: (count: number) => string}>}
 */
export const BUCKETS = [
    {
        key: 'success',
        state: 'success',
        badgeColour: 'green',
        pulse: false,
        title: (count) => `${count} notification(s) have succeeded.`,
    },
    {
        key: 'running',
        state: 'processing',
        badgeColour: 'blue',
        // The only animated thing in the cluster, and only because "still
        // going" is the one bucket whose value is about to change.
        pulse: true,
        title: (count) => `${count} notification(s) are still running.`,
    },
    {
        key: 'error',
        state: 'error',
        badgeColour: 'red',
        pulse: false,
        title: (count) => `${count} notification(s) have failed.`,
    },
]

/**
 * The one bucket to show when there is only room for one - a corner count on a
 * 22px medal, say, where three pills would not fit.
 *
 * Most severe wins: a failure is what is worth the space. Defined here, beside
 * the buckets, so the cluster and the single-count presentation cannot drift
 * apart on which bucket matters or what colour it is.
 *
 * @param {{success?: number, running?: number, error?: number}} [counts]
 * @returns {{key: string, count: number, badgeColour: string, title: string}|null} `null` when every bucket is empty.
 */
export function mostSevereNotificationBucket(counts) {
    const bucket = [...BUCKETS]
        .reverse()
        .find(candidate => (counts?.[candidate.key] ?? 0) > 0)
    if (!bucket) return null
    const count = counts[bucket.key]
    return {key: bucket.key, count, badgeColour: bucket.badgeColour, title: bucket.title(count)}
}

/**
 * @param {Object} props
 * @param {{success?: number, running?: number, error?: number}} [props.counts]
 * @param {string} [props.href] Where a pill leads - the entity's own page.
 * @param {boolean} [props.showText] Spells each bucket out beside its pill.
 *   For the panels that have room; the medals do not.
 */
export default function NotificationBadgeCluster({counts, href, showText = false}) {

    const buckets = BUCKETS
        .map(bucket => ({...bucket, count: counts?.[bucket.key] ?? 0}))
        .filter(bucket => bucket.count > 0)

    // Nothing at all rather than an empty row: an entity with no notifications
    // should take up no space, not a blank the width of three pills.
    if (buckets.length === 0) return null

    return (
        <Space size={4}>
            {
                buckets.map(bucket => {
                    const title = bucket.title(bucket.count)
                    const pill = <StatePill
                        id={`notification-badge-${bucket.key}`}
                        state={bucket.state}
                        text={bucket.count}
                        // The pill names itself only when the sentence is not
                        // already beside it - otherwise a screen reader reads
                        // the same sentence twice, once for the pill and once
                        // for the visible text.
                        title={showText ? undefined : title}
                        href={href}
                        pulse={bucket.pulse}
                    />
                    return showText
                        ? <Space key={bucket.key} size={4}>
                            {pill}
                            <Typography.Text>{title}</Typography.Text>
                        </Space>
                        : <span key={bucket.key}>{pill}</span>
                })
            }
        </Space>
    )
}

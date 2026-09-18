import dayjs from "dayjs"

/**
 * How long a deployment took, as one short string.
 *
 * The Deployments tab has one narrow column for it, so "2h 14m" rather than "2 hours and 14
 * minutes", and two units at most: a reader comparing a column of durations is looking for the
 * outlier, and seconds on a two-hour deployment are noise that makes the column harder to scan.
 *
 * A deployment that has not finished has no duration yet. It is deliberately *not* measured against
 * "now": the row would then change every time the page polled, and a running deployment's elapsed
 * time is the header block's business, not the archive's.
 *
 * @param {string} start When it started.
 * @param {string} end When it ended, or null while it is still going.
 * @return {string} The duration, or an em dash when there is not one.
 */
export const formatDuration = (start, end) => {
    if (!start || !end) return '—'
    const from = dayjs(start)
    const to = dayjs(end)
    if (!from.isValid() || !to.isValid()) return '—'

    const seconds = Math.max(0, to.diff(from, 'second'))
    if (seconds < 60) return `${seconds}s`

    const minutes = Math.floor(seconds / 60)
    if (minutes < 60) {
        const remainingSeconds = seconds % 60
        return remainingSeconds ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`
    }

    const hours = Math.floor(minutes / 60)
    if (hours < 24) {
        const remainingMinutes = minutes % 60
        return remainingMinutes ? `${hours}h ${remainingMinutes}m` : `${hours}h`
    }

    const days = Math.floor(hours / 24)
    const remainingHours = hours % 24
    return remainingHours ? `${days}d ${remainingHours}h` : `${days}d`
}

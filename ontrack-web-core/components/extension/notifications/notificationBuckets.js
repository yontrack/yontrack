// @ts-check

/**
 * Buckets notification record result types into the three counts the badges
 * show.
 *
 * These are NOTIFICATION records across every channel - mail, Slack, webhook,
 * workflow - and not workflow executions. See
 * `docs/adr/0002-promotion-run-badges-count-notifications.md`.
 *
 * @param {ReadonlyArray<string>} [types] Values of `notificationRecord.result.type`.
 * @returns {{success: number, running: number, error: number}}
 */
export function bucketNotificationTypes(types) {
    let success = 0
    let running = 0
    let error = 0
    ;(types ?? []).forEach(type => {
        switch (type) {
            case 'OK':
                success++
                break
            case 'ONGOING':
            case 'ASYNC':
                running++
                break
            default:
                // Deliberately the default rather than an explicit list: a
                // result type this frontend has never heard of is one we cannot
                // vouch for, and counting it as a success would hide it.
                error++
                break
        }
    })
    return {success, running, error}
}

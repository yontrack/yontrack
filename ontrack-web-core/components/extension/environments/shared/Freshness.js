import {useEffect, useRef, useState} from "react"
import {Button, Space, Tooltip, Typography} from "antd"
import {FaSyncAlt} from "react-icons/fa"

/** How often an operational screen asks the server again, in milliseconds. */
export const FRESHNESS_INTERVAL_MS = 30_000

/**
 * The polling half of an operational screen.
 *
 * The matrix, the drawer, the deployment page and the slot page header all show something that
 * changes while a person is looking at it - a deployment moves from candidate to running without
 * anyone touching the page. They poll every 30 seconds and say when they last heard, so a stale
 * screen is visibly stale rather than silently wrong. Push (subscriptions or SSE) replaces this
 * later; the seam is this hook, and nothing above it knows which of the two it is getting.
 *
 * @param {number} intervalMs How long between two automatic refreshes. 0 disables polling, which is
 *   what a test or an embedded rendering wants.
 * @return {{refreshCount: number, refreshedAt: number, refresh: function}} `refreshCount` goes in a
 *   query's `deps`; `refresh` is the manual button; `refreshedAt` is when the last refresh was asked
 *   for.
 */
export const useFreshness = ({intervalMs = FRESHNESS_INTERVAL_MS} = {}) => {
    const [refreshCount, setRefreshCount] = useState(0)
    const [refreshedAt, setRefreshedAt] = useState(() => Date.now())

    const refresh = () => {
        setRefreshedAt(Date.now())
        setRefreshCount(count => count + 1)
    }

    // The timer must not be re-armed by every render, and `refresh` changes identity on each one -
    // hence the ref rather than `refresh` in the dependency list.
    const refreshRef = useRef(refresh)
    refreshRef.current = refresh

    useEffect(() => {
        if (!intervalMs) return undefined
        const timer = setInterval(() => refreshRef.current(), intervalMs)
        return () => clearInterval(timer)
    }, [intervalMs])

    return {refreshCount, refreshedAt, refresh}
}

/**
 * "Updated 12 s ago ⟳" - the visible half of [useFreshness].
 *
 * It ticks on its own once a second so the age stays true between two polls; without that it would
 * read "Updated 0 s ago" for thirty seconds and then jump.
 */
export default function Freshness({refreshedAt, refresh, testId = 'freshness'}) {

    const [, setTick] = useState(0)

    useEffect(() => {
        const timer = setInterval(() => setTick(value => value + 1), 1000)
        return () => clearInterval(timer)
    }, [])

    const seconds = Math.max(0, Math.round((Date.now() - refreshedAt) / 1000))

    return (
        <Space size={4}>
            <Typography.Text type="secondary" data-testid={`${testId}-age`}>
                {`Updated ${seconds} s ago`}
            </Typography.Text>
            <Tooltip title="Refresh now">
                <Button
                    type="text"
                    size="small"
                    icon={<FaSyncAlt/>}
                    data-testid={`${testId}-refresh`}
                    onClick={refresh}
                />
            </Tooltip>
        </Space>
    )
}

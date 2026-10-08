import {useRef} from "react";

/**
 * The latest value which was not `null` nor `undefined`.
 *
 * For what a page refetches on its own - an auto refresh - and must not blank when one refetch
 * fails: `useQuery` nulls its data on an error, and a view reading it straight would trade what it
 * was showing a second ago for an empty one, as a claim about the data rather than about the network.
 *
 * @param value Value to remember
 */
export default function useLastKnown(value) {
    const last = useRef(value)
    if (value !== null && value !== undefined) {
        last.current = value
    }
    return last.current
}

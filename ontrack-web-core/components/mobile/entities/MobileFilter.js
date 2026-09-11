"use client"

/**
 * The name filter every long mobile list carries, and the box it is typed into.
 *
 * An instance holds hundreds of projects, a project holds hundreds of branches
 * and a branch holds thousands of builds - all more than anyone scrolls through
 * on a phone. Every one of those lists filters by name, and every one filters
 * **on the server**: the browser only ever holds the answer to the last query,
 * so a client-side filter could narrow that but never reach a row the server had
 * not already sent.
 *
 * The screens differ in what they send - the project list an `ILIKE` pattern,
 * the branch list and the build search a regular expression (see `namePatterns`)
 * - but not in how the typing is handled, which is what lives here. The
 * subtleties below were each a defect worth a comment, and they should exist
 * once.
 */

import {useEffect, useMemo, useState} from "react"
import debounce from "lodash.debounce"
import {Input} from "antd"

/**
 * How long the typing has to settle before the list is fetched again. Long
 * enough that a word is one request rather than one per letter, short enough
 * that it does not read as lag.
 */
export const FILTER_DEBOUNCE_MS = 400

/**
 * The state behind a mobile list's name filter.
 *
 * @returns {{typed: string, onChange: function, filter: string, filtering: boolean}}
 *   `typed` is what is in the box and `filter` is what has been asked for -
 *   two values, because the second lags the first by the debounce and the input
 *   must not.
 */
export function useMobileFilter() {

    const [typed, setTyped] = useState('')
    const [pattern, setPattern] = useState('')

    const applyPattern = useMemo(
        () => debounce((value) => setPattern(value), FILTER_DEBOUNCE_MS),
        []
    )

    // A timer must not outlive the screen that armed it - leaving the tab within
    // the debounce would otherwise fire a state update into an unmounted tree.
    useEffect(() => () => applyPattern.cancel(), [applyPattern])

    const onChange = (event) => {
        const value = event.target.value
        setTyped(value)
        if (value) {
            applyPattern(value)
        } else {
            // Clearing is immediate: the user asking for the whole list back
            // should not wait on a debounce that is only there to spare the
            // server a request per keystroke.
            applyPattern.cancel()
            setPattern('')
        }
    }

    /*
     * Trimmed, because a lone space is not a filter. The project list's server
     * side tests its pattern with `isNullOrBlank` and answers a blank one with
     * the *whole* list, and a screen that called itself filtered would then head
     * every project on the instance with "Matching projects".
     */
    const filter = pattern.trim()

    return {typed, onChange, filter, filtering: filter.length > 0}
}

/**
 * The box itself.
 *
 * @param {{typed: string, onChange: function}} filter From {@link useMobileFilter}.
 * @param {string} placeholder
 * @param {string} label What a screen reader announces the box as.
 * @param {string} testId
 */
export function MobileFilterInput({filter, placeholder = "Filter by name", label, testId}) {
    return (
        <Input
            allowClear
            value={filter.typed}
            onChange={filter.onChange}
            placeholder={placeholder}
            aria-label={label}
            data-testid={testId}
        />
    )
}

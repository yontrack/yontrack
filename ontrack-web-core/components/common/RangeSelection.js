import {useEffect, useState} from "react";

/**
 * Selection of two elements - two builds, most of the time - as the boundaries of a range.
 *
 * @param available Elements which can be selected, when they can change under the selection - a
 *   list of builds which refreshes itself. An element no longer in it is dropped from the selection,
 *   and only that one. Left out, the selection is kept whatever happens around it.
 */
export default function useRangeSelection({available} = {}) {

    // See https://react.dev/learn/updating-arrays-in-state
    const [selection, setSelection] = useState([])

    // Keyed on the content of the list rather than on its identity, which is new on every render
    const availableKey = available?.join(',')
    useEffect(() => {
        if (available) {
            setSelection(current => {
                const kept = current.filter(x => available.includes(x))
                return kept.length === current.length ? current : kept
            })
        }
    }, [availableKey])

    const isSelected = (x) => selection.indexOf(x) >= 0

    const isComplete = () => selection.length === 2 && selection[0] !== selection[1]

    const select = (x) => {
        // console.log(`Selecting ${x} in ${JSON.stringify(selection)}`)
        if (selection.length === 0) {
            setSelection([x])
        } else {
            const index = selection.indexOf(x)
            if (index >= 0) {
                setSelection(selection.filter(o => o !== x))
            } else if (selection.length === 1) {
                setSelection([...selection, x])
            } else {
                // Replacing the last element only (position 1)
                setSelection([selection[0], x])
            }
        }
    }

    return {
        /**
         * Access to the current selection (read-only)
         */
        selection,
        /**
         * Checks if an element is selected
         */
        isSelected,
        /**
         * Selects one element and adjust the section
         */
        select,
        /**
         * Checks is the selection is commplete
         */
        isComplete,
    }

}
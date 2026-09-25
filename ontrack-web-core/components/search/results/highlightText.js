/**
 * Highlights the words of a query in a text - the title of a search result.
 *
 * Every occurrence of every word of the query is flagged, whatever the case; overlapping
 * occurrences are merged.
 *
 * @return The parts of the text, `{text, match}`, in the shape of the `highlight` the server
 * computes for the free text of a result
 */
export function highlightText(text, query) {
    if (!text) return []
    const words = (query ?? '').trim().split(/\s+/).filter(Boolean)
    if (words.length === 0) return [{text, match: false}]

    // Ranges of the matches, merged
    const lower = text.toLowerCase()
    const ranges = []
    words.forEach(word => {
        const needle = word.toLowerCase()
        let index = lower.indexOf(needle)
        while (index >= 0) {
            ranges.push([index, index + needle.length])
            index = lower.indexOf(needle, index + 1)
        }
    })
    if (ranges.length === 0) return [{text, match: false}]
    ranges.sort((a, b) => a[0] - b[0])
    const merged = []
    ranges.forEach(([start, end]) => {
        const last = merged[merged.length - 1]
        if (last && start <= last[1]) {
            last[1] = Math.max(last[1], end)
        } else {
            merged.push([start, end])
        }
    })

    const parts = []
    let position = 0
    merged.forEach(([start, end]) => {
        if (start > position) parts.push({text: text.substring(position, start), match: false})
        parts.push({text: text.substring(start, end), match: true})
        position = end
    })
    if (position < text.length) parts.push({text: text.substring(position), match: false})
    return parts
}

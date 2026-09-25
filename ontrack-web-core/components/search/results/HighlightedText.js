/**
 * Text with some of its parts highlighted: the `{text, match}` parts of the `highlight` of a search
 * result, or of {@link highlightText}.
 *
 * The parts are rendered as text - whatever markup they contain stays text - the matches in a
 * `mark`.
 */
export default function HighlightedText({parts = []}) {
    return parts.map((part, index) =>
        part.match ?
            <mark key={index} className="ot-search-mark">{part.text}</mark> :
            <span key={index}>{part.text}</span>
    )
}

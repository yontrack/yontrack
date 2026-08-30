import {FaGithub, FaJenkins} from "react-icons/fa";

/**
 * The icons name themselves with `aria-label` rather than react-icons' `title`: an SVG <title>
 * becomes the hover tooltip and would shadow the "Link to ..." tooltip RunInfoSource puts on the
 * surrounding link in minimal mode, losing the only cue that the icon is clickable.
 */
export default function RunInfoSourceTypeIcon({type}) {
    return (
        <>
            {
                type === 'jenkins' && <FaJenkins role="img" aria-label="Jenkins"/>
            }
            {
                // `github-workflow` is what the GitHub ingestion records, and what the CI
                // workflows send through the CLI, so the two land on the same icon.
                type === 'github-workflow' && <FaGithub role="img" aria-label="GitHub"/>
            }
        </>
    )
}

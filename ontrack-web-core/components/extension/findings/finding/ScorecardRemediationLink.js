import Link from "next/link";
import {projectScorecardReadingUri} from "@components/common/Links";
import {useLicensedFeature} from "@components/extension/license/useLicensedFeature";
import {PROJECT_SET_PARAM} from "@components/extension/scorecard/scorecardModel";

/** Licensed feature of the delivery scorecard */
const SCORECARD_FEATURE = 'extension.scorecard'

/** Key of the reading of the remediation time */
const REMEDIATION_TIME = 'security.remediationTime'

/**
 * Link from a finding to the remediation time of its project, on the scorecard page of the project:
 * how fast the project fixes its CRITICAL and HIGH findings, measured on exposure episodes. Shown
 * only when the scorecard is there — its licensed feature enabled.
 */
export default function ScorecardRemediationLink({project}) {
    const {enabled} = useLicensedFeature(SCORECARD_FEATURE)
    if (!enabled || !project) return null
    return (
        <Link
            href={projectScorecardReadingUri(project, PROJECT_SET_PARAM, REMEDIATION_TIME)}
            data-testid="finding-scorecard-remediation-link"
        >
            Remediation time on the scorecard →
        </Link>
    )
}

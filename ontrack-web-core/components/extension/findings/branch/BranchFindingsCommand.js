import {Badge} from "antd";
import {gql} from "graphql-request";
import {FaShieldAlt} from "react-icons/fa";
import {Command} from "@components/common/Commands";
import {projectFindingsUri} from "@components/common/Links";
import {
    findingSeverityColor,
    highestOpenSeverity,
    openFindingsLabel,
} from "@components/extension/findings/findingsModel";

/**
 * What the command needs of a branch, to spread as `...BranchFindingsCommand`.
 */
export const gqlBranchFindingsCommandFragment = gql`
    fragment BranchFindingsCommand on Branch {
        findingsSummary {
            open {
                severity
                count
            }
            openCount
            hasExposures
        }
    }
`

/**
 * "Findings" command of the branch page, leading to the findings open on the branch.
 *
 * Its icon carries the number of open findings in a badge of the colour of the most severe one;
 * its label spells them out, so that the colour never carries the severity alone. Hidden from a
 * user who cannot see the findings (no summary), and for a branch where no finding has ever been
 * reported, so that the many projects without findings do not carry a useless command.
 *
 * @param branch Branch, with its `name`, its `project` and its `findingsSummary`
 */
export default function BranchFindingsCommand({branch}) {
    const summary = branch.findingsSummary
    if (!summary?.hasExposures) return null

    const severity = highestOpenSeverity(summary.open)
    const {background, text} = findingSeverityColor(severity)
    const label = openFindingsLabel(summary.open)

    return (
        <Command
            testId="branch-findings"
            icon={
                // Room for the badge, which overhangs the icon, before the text of the command
                <span style={{paddingInlineEnd: summary.openCount > 0 ? 6 : 0}}>
                    <Badge
                        data-testid="branch-findings-badge"
                        count={summary.openCount}
                        size="small"
                        overflowCount={999}
                        color={background}
                        style={{backgroundColor: background, color: text}}
                    >
                        <FaShieldAlt/>
                    </Badge>
                </span>
            }
            text="Findings"
            title={label}
            ariaLabel={label}
            href={projectFindingsUri(branch.project, {branch: branch.name, state: 'OPEN'})}
        />
    )
}

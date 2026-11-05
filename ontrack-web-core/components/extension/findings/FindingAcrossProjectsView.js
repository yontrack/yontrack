import {gql} from "graphql-request";
import {Alert, Button, Input, Skeleton, Space} from "antd";
import {useQuery} from "@components/services/GraphQL";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import FindingFanOutTable, {gqlFindingFanOutFields} from "@components/extension/findings/FindingFanOutTable";
import FindingsSearchResult from "@components/extension/findings/FindingsSearchResult";
import {fanOutExternalId} from "@components/extension/findings/findingFanOutModel";

/**
 * Number of findings listed for a search
 */
const FINDINGS_LIST_SIZE = 20

export const gqlSearchedFindings = gql`
    query SearchedFindings($text: String!, $size: Int!) {
        searchedFindings(text: $text, size: $size) {
            externalId
            title
            severity
            openProjects
            acceptedProjects
            resolvedProjects
            firstSeen
        }
    }
`

export const gqlFindingAcrossProjects = gql`
    query FindingAcrossProjects($externalId: String!) {
        findings(externalId: $externalId) {
            ...FindingFanOutFields
        }
    }
    ${gqlFindingFanOutFields}
`

/**
 * `2 projects report CVE-1: exposed in 1, accepted in 0, resolved in 1`
 */
const summaryText = (externalId, {projects, exposed, accepted, resolved}) =>
    `${projects} ${projects === 1 ? 'project reports' : 'projects report'} ${externalId}: ` +
    `exposed in ${exposed}, accepted in ${accepted}, resolved in ${resolved}`

function SearchedFindings({text, onSelect, onOnlyOne}) {

    const {data: findings, finished, error} = useQuery(
        gqlSearchedFindings,
        {
            variables: {text, size: FINDINGS_LIST_SIZE},
            deps: [text],
            dataFn: data => data.searchedFindings ?? [],
        }
    )

    return <FindingsSearchResult
        findings={findings}
        finished={finished}
        error={error}
        text={text}
        size={FINDINGS_LIST_SIZE}
        emptyDescription={`No finding has an external ID containing "${text}".`}
        testId="finding-fanout-searched"
        onSelect={onSelect}
        onOnlyOne={onOnlyOne}
    />
}

function FanOut({externalId}) {

    const {data: findings, finished, error} = useQuery(
        gqlFindingAcrossProjects,
        {
            variables: {externalId},
            deps: [externalId],
            dataFn: data => data.findings ?? [],
        }
    )

    if (!finished) {
        return <Skeleton active/>
    } else if (error) {
        return <Alert type="error" showIcon title={error}/>
    }
    return <FindingFanOutTable
        findings={findings}
        summaryText={summary => summaryText(externalId, summary)}
        emptyDescription={`No project reports ${externalId}.`}
        testId="finding-fanout"
    />
}

/**
 * One finding across all the projects: picked by its external ID (a CVE, a rule ID) or a part of it,
 * ignoring case, then the projects reporting it, on which branches, since when — among the projects
 * whose findings the user is granted the view of.
 *
 * The finding and the search are kept by the page, in its URL, so that a fan-out or a search can be
 * shared by its link.
 *
 * @param finding External ID of the finding whose fan-out is shown, `null` for none
 * @param search Text searched among the external IDs, `null` for none. Kept with the finding picked
 * among its results, to go back to them.
 * @param onChange Called with the new `{finding, search}`
 */
export default function FindingAcrossProjectsView({finding = null, search = null, onChange}) {
    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}} data-testid="finding-fanout">
            <Space size={16} wrap>
                {/*
                  * `data-testid` on an antd 6 `Input.Search` lands on its `Space.Compact` wrapper,
                  * not on the `<input>`: a test types into the searchbox inside it.
                  *
                  * Keyed by what it shows, so that it says the finding shown when it is picked
                  * elsewhere than in it — a row of the findings, the URL.
                  */}
                <Input.Search
                    key={finding ?? search ?? ''}
                    data-testid="finding-fanout-search"
                    style={{maxWidth: 420}}
                    allowClear
                    enterButton="Search"
                    aria-label="External ID of the finding, or part of it"
                    placeholder="External ID or part of it, like CVE-2024-38816 or csrf"
                    defaultValue={finding ?? search ?? ''}
                    onSearch={value => onChange({finding: null, search: fanOutExternalId(value)})}
                />
                {
                    finding && search &&
                    <Button
                        type="link"
                        data-testid="finding-fanout-back-search"
                        onClick={() => onChange({finding: null, search})}
                    >
                        {`Findings containing "${search}"`}
                    </Button>
                }
            </Space>
            {
                !finding && !search &&
                <SecondaryText data-testid="finding-fanout-explanation">
                    Search a finding by its external ID, like a CVE or a rule ID, or by a part of it, to see the
                    projects reporting it, on which branches, and since when. Only the projects whose findings you
                    can see are listed.
                </SecondaryText>
            }
            {
                finding ?
                    <FanOut externalId={finding}/> :
                    search &&
                    <SearchedFindings
                        // A new search is a new list: not the rows of the previous one while it loads
                        key={search}
                        text={search}
                        onSelect={externalId => onChange({finding: externalId, search})}
                        // The only finding found replaces its search: going back to it would open it again
                        onOnlyOne={externalId => onChange({finding: externalId, search: null})}
                    />
            }
        </Space>
    )
}

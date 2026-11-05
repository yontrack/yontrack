import {gql} from "graphql-request";
import {Alert, Button, Empty, Input, Skeleton, Space} from "antd";
import {useQuery} from "@components/services/GraphQL";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import FindingFanOutTable, {gqlFindingFanOutFields} from "@components/extension/findings/FindingFanOutTable";
import FindingsRankTable from "@components/extension/findings/FindingsRankTable";
import FindingsSearchResult from "@components/extension/findings/FindingsSearchResult";
import {fanOutExternalId, rankedFindingRows} from "@components/extension/findings/findingFanOutModel";
import {rankedFindingsCaption} from "@components/extension/scorecard/estates/estateViewModel";

/**
 * Number of ranked findings listed when nothing is searched, and of findings listed for a search
 */
const FINDINGS_LIST_SIZE = 20

export const gqlEstateRankedFindings = gql`
    query EstateRankedFindings($name: String!, $size: Int!) {
        estate(name: $name) {
            rankedFindings(size: $size) {
                externalId
                title
                severity
                openProjects
                acceptedProjects
                resolvedProjects
                firstSeen
            }
        }
    }
`

export const gqlEstateSearchedFindings = gql`
    query EstateSearchedFindings($name: String!, $text: String!, $size: Int!) {
        estate(name: $name) {
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
    }
`

export const gqlEstateFindingsFanOut = gql`
    query EstateFindingsFanOut($name: String!, $externalId: String!) {
        estate(name: $name) {
            findings(externalId: $externalId) {
                ...FindingFanOutFields
            }
        }
    }
    ${gqlFindingFanOutFields}
`

/**
 * `2 projects of this estate report CVE-1: exposed in 1, accepted in 0, resolved in 1`
 */
const summaryText = (externalId, {projects, exposed, accepted, resolved}) =>
    `${projects} ${projects === 1 ? 'project of this estate reports' : 'projects of this estate report'} ${externalId}: ` +
    `exposed in ${exposed}, accepted in ${accepted}, resolved in ${resolved}`

/**
 * The findings open in the most projects of the estate, one row per external ID: a click on a row
 * opens the fan-out of its finding.
 */
function RankedFindings({estate, onSelect}) {

    const {data: rankedFindings, finished, error} = useQuery(
        gqlEstateRankedFindings,
        {
            variables: {name: estate.name, size: FINDINGS_LIST_SIZE},
            deps: [estate.name],
            condition: true,
            dataFn: data => data.estate?.rankedFindings ?? [],
        }
    )

    if (!finished) {
        return <Skeleton active/>
    } else if (error) {
        return <Alert type="error" showIcon title={error}/>
    }

    const rows = rankedFindingRows(rankedFindings)
    if (rows.length === 0) {
        return <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="No finding is open in the projects of this estate."
        />
    }

    return <FindingsRankTable
        rows={rows}
        caption={rankedFindingsCaption(rows.length, FINDINGS_LIST_SIZE)}
        testId="estate-ranked"
        onSelect={onSelect}
    />
}

/**
 * The findings of the estate whose external ID contains a text, ignoring case, open or not, one row
 * per external ID, ranked as the ranked findings are: a click on a row opens the fan-out of its
 * finding. When only one finding is found, its fan-out is opened right away.
 *
 * @param estate Estate, with its `name`
 * @param text Text searched among the external IDs
 * @param onSelect Called with the external ID of the finding whose row is clicked
 * @param onOnlyOne Called with the external ID of the only finding found
 */
function SearchedFindings({estate, text, onSelect, onOnlyOne}) {

    const {data: searchedFindings, finished, error} = useQuery(
        gqlEstateSearchedFindings,
        {
            variables: {name: estate.name, text, size: FINDINGS_LIST_SIZE},
            deps: [estate.name, text],
            condition: true,
            dataFn: data => data.estate?.searchedFindings ?? [],
        }
    )

    return <FindingsSearchResult
        findings={searchedFindings}
        finished={finished}
        error={error}
        text={text}
        size={FINDINGS_LIST_SIZE}
        emptyDescription={`No finding of this estate has an external ID containing "${text}".`}
        testId="estate-searched"
        onSelect={onSelect}
        onOnlyOne={onOnlyOne}
    />
}

/**
 * The findings fan-out of an estate: one finding, picked among the findings open in the most
 * projects of the estate, or among the ones whose external ID (a CVE, a rule ID) contains the text
 * searched, ignoring case, and the projects of the estate exposed to it, on which branches, since
 * when — among the projects the user can see and whose findings the user is granted the view of.
 *
 * The finding and the search are kept by the parent — the estate page keeps them in its URL, so
 * that a fan-out or a search can be shared by its link.
 *
 * @param estate Estate, with its `name`
 * @param finding External ID of the finding whose fan-out is shown, `null` for none
 * @param search Text searched among the external IDs, `null` for the ranked findings. Kept with the
 * finding picked among its results, to go back to them.
 * @param onChange Called with the new `{finding, search}`
 */
export default function EstateFindingsFanOut({estate, finding = null, search = null, onChange}) {

    const {data: findings, finished, error} = useQuery(
        gqlEstateFindingsFanOut,
        {
            variables: {name: estate.name, externalId: finding},
            deps: [estate.name, finding],
            condition: !!finding,
            dataFn: data => data.estate?.findings ?? [],
        }
    )

    let result
    if (finding) {
        if (!finished) {
            result = <Skeleton active/>
        } else if (error) {
            result = <Alert type="error" showIcon title={error}/>
        } else {
            result = <FindingFanOutTable
                findings={findings ?? []}
                summaryText={summary => summaryText(finding, summary)}
                emptyDescription={`No project of this estate reports ${finding}.`}
                testId="estate-fanout"
            />
        }
    } else if (search) {
        result = <SearchedFindings
            // A new search is a new list: not the rows of the previous one while it loads
            key={search}
            estate={estate}
            text={search}
            onSelect={externalId => onChange({finding: externalId, search})}
            // The only finding found replaces its search: going back to it would open it again
            onOnlyOne={externalId => onChange({finding: externalId, search: null})}
        />
    } else {
        result = <RankedFindings estate={estate} onSelect={externalId => onChange({finding: externalId, search: null})}/>
    }

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}} data-testid="estate-fanout">
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
                    data-testid="estate-fanout-search"
                    style={{maxWidth: 420}}
                    allowClear
                    enterButton="Search"
                    aria-label="External ID of the finding, or part of it"
                    placeholder="External ID or part of it, like CVE-2021-44228 or csrf"
                    defaultValue={finding ?? search ?? ''}
                    onSearch={value => onChange({finding: null, search: fanOutExternalId(value)})}
                />
                {
                    finding && search &&
                    <Button
                        type="link"
                        data-testid="estate-fanout-back-search"
                        onClick={() => onChange({finding: null, search})}
                    >
                        {`Findings containing "${search}"`}
                    </Button>
                }
                {
                    (finding || search) &&
                    <Button
                        type="link"
                        data-testid="estate-fanout-back"
                        onClick={() => onChange({finding: null, search: null})}
                    >
                        All open findings
                    </Button>
                }
            </Space>
            {
                !finding && !search &&
                <SecondaryText>
                    Search a finding by its external ID, like a CVE or a rule ID, or by a part of it, or pick one
                    below, to see the projects of this estate exposed to it, on which branches, and since when.
                </SecondaryText>
            }
            {result}
        </Space>
    )
}

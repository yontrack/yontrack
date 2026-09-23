import {useState} from "react";
import {useRouter} from "next/router";
import Head from "next/head";
import {gql} from "graphql-request";
import {Alert, Space, Typography} from "antd";
import MainPage from "@components/layouts/MainPage";
import {CloseCommand} from "@components/common/Commands";
import {projectUri} from "@components/common/Links";
import {projectTitle} from "@components/common/Titles";
import {downToProjectBreadcrumbs} from "@components/common/Breadcrumbs";
import {useQuery} from "@components/services/GraphQL";
import {isAuthorized} from "@components/common/authorizations";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";
import {findingsFilterFromQuery, findingsFilterToQuery} from "@components/extension/findings/findingsModel";
import ProjectFindingsFilter from "@components/extension/findings/project/ProjectFindingsFilter";
import ProjectFindingsTable from "@components/extension/findings/project/ProjectFindingsTable";

const DEFAULT_PAGE_SIZE = 20

/**
 * The findings page of a project: its findings in a table, filtered by severity, state, branch,
 * scanner and kind.
 *
 * The filter lives in the query of the URL — see `projectFindingsUri` — so that the Security
 * section of the project page can link to any of its counts, and a filtered list can be shared.
 */
export default function ProjectFindingsView({id}) {

    const router = useRouter()
    const filter = findingsFilterFromQuery(router.query)
    const filterKey = JSON.stringify(filter)

    // The page goes back to the first one when the filter changes: kept together with the filter
    // it was chosen for, rather than reset by an effect
    const [pagination, setPagination] = useState({filterKey, current: 1, pageSize: DEFAULT_PAGE_SIZE})
    const current = pagination.filterKey === filterKey ? pagination.current : 1
    const pageSize = pagination.pageSize

    const {data: project, loading: projectLoading, finished: projectFinished} = useQuery(
        gql`
            query ProjectFindingsProject($id: Int!) {
                project(id: $id) {
                    ...ProjectContent
                    authorizations {
                        name
                        action
                        authorized
                    }
                    findingsSummary {
                        scanners
                        branches {
                            branch {
                                id
                                name
                            }
                        }
                    }
                }
            }
            ${gqlProjectContentFragment}
        `,
        {
            variables: {id},
            deps: [id],
            condition: !!id,
            dataFn: data => data.project,
        }
    )

    const {data: page, loading, error} = useQuery(
        gql`
            query ProjectFindings($id: Int!, $filter: FindingFilter, $offset: Int!, $size: Int!) {
                project(id: $id) {
                    findings(filter: $filter, offset: $offset, size: $size) {
                        pageInfo {
                            totalSize
                        }
                        pageItems {
                            id
                            scanner
                            externalId
                            location
                            kind
                            title
                            lastSeen
                            maxSeverity
                            state
                            exposures {
                                branch {
                                    id
                                    name
                                }
                                state
                            }
                        }
                    }
                }
            }
        `,
        {
            variables: {id, filter, offset: (current - 1) * pageSize, size: pageSize},
            deps: [id, filterKey, current, pageSize],
            condition: !!id,
            dataFn: data => data.project?.findings,
        }
    )

    const onFilterChange = (field, value) => {
        const query = findingsFilterToQuery({...filter, [field]: value})
        router.replace(
            {pathname: router.pathname, query: {...query, id: router.query.id}},
            undefined,
            {shallow: true},
        )
    }

    const onPageChange = (newCurrent, newPageSize) => {
        setPagination({filterKey, current: newPageSize !== pageSize ? 1 : newCurrent, pageSize: newPageSize})
    }

    const branches = (project?.findingsSummary?.branches ?? [])
        .map(it => it.branch.name)
        .sort((a, b) => a.localeCompare(b))
    const scanners = project?.findingsSummary?.scanners ?? []
    const allowed = !project || isAuthorized(project, 'findings', 'view')

    return (
        <>
            <Head>
                {project && projectTitle(project, "Security findings")}
            </Head>
            <MainPage
                title="Security findings"
                breadcrumbs={project ? downToProjectBreadcrumbs({project}) : []}
                commands={[
                    <CloseCommand key="close" href={projectUri({id})}/>,
                ]}
            >
                <Space orientation="vertical" size={16} className="ot-line">
                    {
                        !allowed && projectFinished &&
                        <Alert
                            type="warning"
                            showIcon
                            title="You are not allowed to see the security findings of this project."
                        />
                    }
                    {
                        allowed &&
                        <>
                            <ProjectFindingsFilter
                                filter={filter}
                                branches={branches}
                                scanners={scanners}
                                onChange={onFilterChange}
                            />
                            {
                                filter.branch && filter.state &&
                                <Typography.Text type="secondary">
                                    The state is the one on the branch {filter.branch}; the State column gives the one in the project.
                                </Typography.Text>
                            }
                            {
                                error &&
                                <Alert type="error" showIcon title={error}/>
                            }
                            <ProjectFindingsTable
                                findings={page?.pageItems ?? []}
                                totalSize={page?.pageInfo?.totalSize ?? 0}
                                loading={loading || projectLoading}
                                current={current}
                                pageSize={pageSize}
                                onPageChange={onPageChange}
                            />
                        </>
                    }
                </Space>
            </MainPage>
        </>
    )
}

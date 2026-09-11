"use client"

/**
 * One project, on a phone: its branches.
 *
 * The desktop project page carries branch boxes with their last promotions,
 * decorations, an info drawer and a row of commands. None of that is what
 * someone opens on a phone for - they are on their way to a branch, and from
 * there to a build. So this screen is the mobile list shape and nothing else: a
 * branch per row, its star, and a tap through to it.
 *
 * The branch list is **limited and filterable**, for the same reason the project
 * list is: a project can hold hundreds of branches, and a phone shows a handful
 * of rows. The limit keeps the screen honest about that rather than pretending
 * to be a full list, and the filter is how a user reaches the branch the limit
 * left out. Both run on the server - see `MobileFilter` and `namePatterns`.
 */

import {gql} from "graphql-request"
import {Space, Tag, Typography} from "antd"
import {useQuery} from "@components/services/GraphQL"
import MobileScreen from "@components/mobile/layout/MobileScreen"
import MobileAsyncContent from "@components/mobile/layout/MobileAsyncContent"
import MobileEmpty from "@components/mobile/layout/MobileEmpty"
import {MobileEntityGroup, MobileEntityRow} from "@components/mobile/entities/MobileEntityList"
import {MobileFilterInput, useMobileFilter} from "@components/mobile/entities/MobileFilter"
import {branchNamePattern} from "@components/mobile/entities/namePatterns"
import MobileFavourite from "@components/mobile/favourites/MobileFavourite"
import {useFavouriteRefresh} from "@components/mobile/favourites/useFavouriteRefresh"
import {mobileBranchUri} from "@components/mobile/mobileRoutes"

/**
 * How many branches the screen shows at once.
 *
 * Enough to cover a project's living branches - the list is ordered by build
 * activity, so those come first - without turning the screen into something to
 * be scrolled rather than read. Beyond it, the filter is the way through.
 */
export const MOBILE_BRANCH_LIMIT = 20

/**
 * The line under a branch's name: its own name when that is not what is shown,
 * and whether it is disabled.
 *
 * The name matters because the **filter matches it**, not the display name -
 * `branches(name:)` runs against `BRANCHES.NAME`. A branch showing as `PRJ-1234`
 * and named `feature/PRJ-1234-search` would otherwise look like it ignored a
 * filter that in fact matched it, or refused one it could not.
 */
function BranchContext({branch}) {
    const named = branch.displayName && branch.displayName !== branch.name
    if (!named && !branch.disabled) return undefined
    return (
        <Space size={6}>
            {named && branch.name}
            {branch.disabled && <Tag color="default">Disabled</Tag>}
        </Space>
    )
}

export default function MobileProjectScreen({id}) {

    const filter = useMobileFilter()

    const {refresh, onToggled} = useFavouriteRefresh()

    const query = useQuery(
        gql`
            query MobileProject($id: Int!, $name: String, $count: Int!) {
                project(id: $id) {
                    id
                    name
                    favourite
                    # Ordered by the most recent build activity, so what the
                    # limit below keeps is what someone is most likely here for.
                    branches(name: $name, count: $count, order: true) {
                        id
                        name
                        displayName
                        disabled
                        favourite
                    }
                }
            }
        `,
        {
            variables: {
                // `null` and not `''` - the server treats a blank name as no
                // filter at all, so sending one would be a lie the screen then
                // has to tell in its heading too.
                name: branchNamePattern(filter.filter),
                /*
                 * One more than is shown. `branches` answers with a plain list
                 * and no total, so the extra row is the only way to know whether
                 * anything was left out - and it costs exactly one row.
                 */
                count: MOBILE_BRANCH_LIMIT + 1,
                id: Number(id),
            },
            deps: [id, filter.filter, refresh],
        }
    )

    /*
     * No "no such project" state of its own, deliberately. `project(id:)` is a
     * nullable field, but the server never answers a bad id with a null: it
     * raises `ProjectNotFoundException` (and `AccessDeniedException` for one the
     * user cannot see), which arrive as GraphQL *errors* carrying the reason.
     * The error alert below therefore already says what happened, and a
     * null-checking branch beside it would be code that never runs.
     */
    const project = query.data?.project

    const found = project?.branches ?? []
    const branches = found.slice(0, MOBILE_BRANCH_LIMIT)
    const truncated = found.length > MOBILE_BRANCH_LIMIT

    return (
        <MobileScreen
            title={project?.name ?? "Project"}
            extra={
                project &&
                <MobileFavourite
                    type="project"
                    id={project.id}
                    name={project.name}
                    favourite={project.favourite}
                    onToggled={onToggled}
                />
            }
        >
            <MobileFilterInput
                filter={filter}
                label="Filter the branches by name"
                testId="mobile-branches-filter"
            />
            <MobileAsyncContent
                state={query}
                errorMessage="Could not load the project."
                isEmpty={branches.length === 0}
                rows={6}
                empty={
                    <MobileEmpty
                        testId="mobile-branches-empty"
                        description={
                            // Two different facts, and telling them apart is the
                            // difference between "type something else" and
                            // "there is nothing here to find".
                            filter.filtering
                                ? `No branch matches "${filter.filter}".`
                                : "This project has no branch yet."
                        }
                    />
                }
            >
                <MobileEntityGroup
                    title={filter.filtering ? "Matching branches" : "Branches"}
                    testId="mobile-branches"
                >
                    {
                        branches.map(branch =>
                            <MobileEntityRow
                                key={branch.id}
                                testId={`mobile-branch-${branch.id}`}
                                name={branch.displayName || branch.name}
                                href={mobileBranchUri(branch.id)}
                                context={<BranchContext branch={branch}/>}
                                action={
                                    <MobileFavourite
                                        type="branch"
                                        id={branch.id}
                                        name={branch.displayName || branch.name}
                                        favourite={branch.favourite}
                                        onToggled={onToggled}
                                    />
                                }
                            />
                        )
                    }
                </MobileEntityGroup>
                {
                    truncated &&
                    <div className="ot-mobile-note" data-testid="mobile-branches-truncated">
                        <Typography.Text type="secondary">
                            {`Showing the ${MOBILE_BRANCH_LIMIT} most recently active branches. Filter by name to find another.`}
                        </Typography.Text>
                    </div>
                }
            </MobileAsyncContent>
        </MobileScreen>
    )
}

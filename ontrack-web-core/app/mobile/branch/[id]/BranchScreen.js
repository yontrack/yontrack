"use client"

/**
 * One branch, on a phone: its latest builds.
 *
 * This is the screen that most needs to diverge from the desktop UI, and the
 * only one that does more than restyle it. `BranchBuilds` is a matrix with a
 * column per validation stamp - wide by construction - so a phone gets a card
 * per build instead. See `MobileBuildCard` for what a card carries and what it
 * deliberately leaves to the build screen.
 *
 * The screen carries a **deliberately minimal build search**: a name and one
 * promotion level, and nothing else `StandardBuildFilter` supports - see
 * `MobileBuildFilter`.
 *
 * Everything else the desktop branch page offers - the rest of the build
 * filters, the validation stamp filters, branch links, the delivery map, the
 * change log - is out of scope here and reached through the interstitial.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import Link from "next/link"
import {Button, Spin, Tag} from "antd"
import {useQuery} from "@components/services/GraphQL"
import MobileScreen from "@components/mobile/layout/MobileScreen"
import MobileAsyncContent from "@components/mobile/layout/MobileAsyncContent"
import MobileEmpty from "@components/mobile/layout/MobileEmpty"
import MobileFavourite from "@components/mobile/favourites/MobileFavourite"
import {useFavouriteRefresh} from "@components/mobile/favourites/useFavouriteRefresh"
import MobileBuildCard from "@components/mobile/builds/MobileBuildCard"
import {MobileBuildFilterControls, useMobileBuildFilter} from "@components/mobile/builds/MobileBuildFilter"
import {useMobileBranchDeployments} from "@components/mobile/builds/useMobileDeployments"
import {mobileProjectUri} from "@components/mobile/mobileRoutes"

/**
 * How many builds one page holds.
 *
 * A phone screen shows three or four cards at a time, so a page is a few
 * flicks' worth: enough that scrolling is not immediately interrupted, small
 * enough that the first answer arrives quickly on a phone network.
 */
export const MOBILE_BUILD_PAGE_SIZE = 10

export default function MobileBranchScreen({id}) {

    /*
     * "Load more" grows the page rather than accumulating pages in the browser.
     * Merging pages by hand means owning a second copy of the list and keeping
     * it in step with the favourite toggle's refetches; refetching a longer
     * first page cannot drift, and at these sizes it costs one query against an
     * index the branch page already hits.
     */
    const [size, setSize] = useState(MOBILE_BUILD_PAGE_SIZE)

    const filter = useMobileBuildFilter()

    /*
     * A new filter starts again at the first page. Without this, someone who
     * pressed "Load more" five times and then typed would get fifty filtered
     * builds in one request - and the page size exists because a phone network
     * is what is fetching it.
     *
     * Adjusted during the render rather than in an effect, which is what React
     * prescribes for state that has to follow something else: an effect would
     * let one request go out for the new filter at the old size before resetting
     * it, and that request is exactly the one this guards against.
     */
    const [pagedFor, setPagedFor] = useState(filter.signature)
    if (pagedFor !== filter.signature) {
        setPagedFor(filter.signature)
        setSize(MOBILE_BUILD_PAGE_SIZE)
    }

    const {refresh, onToggled} = useFavouriteRefresh()

    const query = useQuery(
        gql`
            query MobileBranch($id: Int!, $size: Int!, $filter: StandardBuildFilter) {
                branch(id: $id) {
                    id
                    name
                    displayName
                    disabled
                    favourite
                    project {
                        id
                        name
                    }
                    # What the promotion control offers. The branch's own levels,
                    # not the ones the builds on this page happen to carry: a
                    # level nobody has reached recently is precisely the one
                    # worth filtering on.
                    promotionLevels {
                        id
                        name
                        image
                    }
                    buildsPaginated(offset: 0, size: $size, filter: $filter) {
                        pageInfo {
                            nextPage {
                                offset
                            }
                        }
                        pageItems {
                            id
                            name
                            # Already the release property when there is one, and
                            # the build's own name otherwise - a build name is a
                            # timestamp-run pair, not a version.
                            displayName
                            creation {
                                time
                            }
                            # The last run per level: a build promoted twice to
                            # the same level is still at that level once.
                            promotionRuns(lastPerLevel: true) {
                                id
                                promotionLevel {
                                    id
                                    name
                                    image
                                }
                            }
                        }
                    }
                }
            }
        `,
        {
            /*
             * `null` when nothing is filtered, so an unfiltered screen asks the
             * server exactly what it asked before this search existed - the
             * field then builds its own default filter, as it always did.
             */
            variables: {id: Number(id), size, filter: filter.input},
            deps: [id, size, filter.signature, refresh],
        }
    )

    /*
     * Deployments are a query of their own, and deliberately - see
     * `useMobileDeployments`. `currentDeployments` is absent from the schema on
     * an instance with no environments licence, which would fail the document
     * above and take the whole screen with it rather than one badge strip.
     */
    const deploymentsByBuild = useMobileBranchDeployments(id, size, filter)

    const branch = query.data?.branch
    const builds = branch?.buildsPaginated?.pageItems ?? []
    const hasMore = Boolean(branch?.buildsPaginated?.pageInfo?.nextPage)
    const promotionLevels = branch?.promotionLevels ?? []

    const branchName = branch ? (branch.displayName || branch.name) : "Branch"

    return (
        <MobileScreen
            title={branchName}
            subtitle={
                branch?.project &&
                <Link href={mobileProjectUri(branch.project.id)}>
                    {branch.project.name}
                </Link>
            }
            extra={
                branch &&
                <MobileFavourite
                    type="branch"
                    id={branch.id}
                    name={branchName}
                    favourite={branch.favourite}
                    onToggled={onToggled}
                />
            }
        >
            {
                branch?.disabled &&
                <Tag color="default" data-testid="mobile-branch-disabled">Disabled</Tag>
            }
            <MobileBuildFilterControls filter={filter} promotionLevels={promotionLevels}/>
            <MobileAsyncContent
                state={query}
                /*
                 * Also what a missing branch looks like: the root `branch(id:)`
                 * field is non-null, so an id nobody can see comes back as a
                 * GraphQL error rather than as a null - unlike the project
                 * screen, which has a nullable field and its own "no such
                 * project" state.
                 */
                errorMessage="Could not load the branch."
                isEmpty={builds.length === 0}
                rows={6}
                empty={
                    <MobileEmpty
                        testId="mobile-builds-empty"
                        description={
                            // Two different facts, and telling them apart is the
                            // difference between "search for something else" and
                            // "there is nothing here to find".
                            filter.filtering
                                ? filter.noMatch
                                : "This branch has no build yet."
                        }
                    />
                }
            >
                <ul className="ot-mobile-cards" data-testid="mobile-builds">
                    {
                        builds.map(build =>
                            <MobileBuildCard
                                key={build.id}
                                build={build}
                                deployments={deploymentsByBuild[String(build.id)]}
                            />
                        )
                    }
                </ul>
                {
                    hasMore &&
                    <div className="ot-mobile-note">
                        <Button
                            block
                            data-testid="mobile-builds-more"
                            disabled={query.loading}
                            onClick={() => setSize(current => current + MOBILE_BUILD_PAGE_SIZE)}
                        >
                            {query.loading ? <Spin size="small"/> : "Load more"}
                        </Button>
                    </div>
                }
            </MobileAsyncContent>
        </MobileScreen>
    )
}

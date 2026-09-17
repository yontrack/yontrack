"use client"

/**
 * The project list: every project the user can see, one tap from the home screen.
 *
 * It is home's counterpart. Home is the short, curated list; this is the whole
 * one, and the only place a user with no favourites yet can make some - which is
 * why it carries the favourite toggle and is what the empty state points at.
 *
 * An instance can hold hundreds of projects, which is more than anyone scrolls
 * through on a phone, so the list is filterable **by name and by label**. Both
 * filters run on the server (`paginatedProjects`, which ANDs them): a
 * client-side one could only narrow the answer to the last query and would never
 * reach a project the server had not already sent. The typing itself is handled
 * by `useMobileFilter`, shared with the project screen's branch filter.
 *
 * The labels are picked rather than typed, through the same `SelectLabel` the
 * desktop project list filter uses. A phone user cannot be expected to remember
 * the exact `category:name` string of a label, and a project name may contain a
 * colon - so a token typed into the name box would be both undiscoverable and
 * ambiguous, where a picker lists what exists and hands the server the exact
 * display strings its filter takes. Nothing else label-related is on this
 * screen: no chip per row, which would cost the project name the width it needs.
 *
 * Each row goes to that project's mobile screen, which is where its branches are.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import {Tag, Typography} from "antd"
import {useQuery} from "@components/services/GraphQL"
import MobileScreen from "@components/mobile/layout/MobileScreen"
import MobileAsyncContent from "@components/mobile/layout/MobileAsyncContent"
import MobileEmpty from "@components/mobile/layout/MobileEmpty"
import {MobileEntityGroup, MobileEntityRow} from "@components/mobile/entities/MobileEntityList"
import {MobileFilterInput, useMobileFilter} from "@components/mobile/entities/MobileFilter"
import MobileFavourite from "@components/mobile/favourites/MobileFavourite"
import {useFavouriteRefresh} from "@components/mobile/favourites/useFavouriteRefresh"
import {mobileProjectUri} from "@components/mobile/mobileRoutes"
import SelectLabel from "@components/labels/SelectLabel"

/**
 * How many projects the screen asks for at once.
 *
 * `paginatedProjects` is paginated where `projects(pattern:)` was not, and this
 * screen deliberately does not page: a phone list is scrolled, not paged, and a
 * "next page" control on a list whose two filters already narrow it would be a
 * third way to move through it. So one page, sized well past what any instance
 * holds today, and a note when even that was not enough - the same shape the
 * branch list uses, and the same answer: filter.
 */
export const MOBILE_PROJECT_PAGE_SIZE = 200

export default function MobileProjectListScreen() {

    const filter = useMobileFilter()

    // What is picked in the label filter, as `category:name` display strings -
    // exactly what `paginatedProjects(labels:)` takes.
    const [labels, setLabels] = useState([])

    const {refresh, onToggled} = useFavouriteRefresh()

    const query = useQuery(
        gql`
            query MobileProjects($name: String, $labels: [String!], $size: Int!) {
                paginatedProjects(offset: 0, size: $size, name: $name, labels: $labels) {
                    pageInfo {
                        totalSize
                    }
                    pageItems {
                        id
                        name
                        disabled
                        favourite
                    }
                }
            }
        `,
        {
            variables: {
                /*
                 * `null` and not `''` when nothing is typed: the server answers a
                 * blank name with the whole list, and a screen that called itself
                 * filtered would then head every project on the instance with
                 * "Matching projects".
                 */
                name: filter.filtering ? filter.filter : null,
                // An empty selection is no label filter at all, which is what the
                // server makes of an empty list.
                labels,
                size: MOBILE_PROJECT_PAGE_SIZE,
            },
            deps: [filter.filter, labels, refresh],
            dataFn: data => data.paginatedProjects,
        }
    )

    const projects = query.data?.pageItems ?? []

    /*
     * Straight from the answer rather than from a state: the total is the
     * *filtered* one, so it changes with every query, and a state filled by an
     * effect would be one render behind the list it describes.
     */
    const total = query.data?.pageInfo?.totalSize ?? projects.length
    const truncated = total > projects.length

    // Two criteria, one filter: either of them makes the list a filtered one.
    const filtering = filter.filtering || labels.length > 0

    // What was asked for, in the words the user used - a name they typed and the
    // labels they picked read back the same way they went in.
    const criteria = [
        filter.filtering ? `"${filter.filter}"` : null,
        labels.length > 0 ? labels.join(' and ') : null,
    ].filter(criterion => criterion).join(' and ')

    return (
        <MobileScreen title="Projects">
            <MobileFilterInput
                filter={filter}
                label="Filter the projects by name"
                testId="mobile-projects-filter"
            />
            <div data-testid="mobile-projects-labels-filter">
                <SelectLabel
                    multiple
                    value={labels}
                    onChange={setLabels}
                    placeholder="Filter by labels"
                    style={{width: '100%'}}
                />
            </div>
            <MobileAsyncContent
                state={query}
                errorMessage="Could not load the projects."
                isEmpty={projects.length === 0}
                rows={6}
                empty={
                    <MobileEmpty
                        testId="mobile-projects-empty"
                        description={
                            // Two different facts, and telling them apart is the
                            // difference between "ask for something else" and
                            // "there is nothing here to find".
                            filtering
                                ? `No project matches ${criteria}.`
                                : "There is no project on this instance yet."
                        }
                    />
                }
            >
                <MobileEntityGroup
                    title={filtering ? "Matching projects" : "All projects"}
                    testId="mobile-projects"
                >
                    {
                        projects.map(project =>
                            <MobileEntityRow
                                key={project.id}
                                testId={`mobile-project-${project.id}`}
                                name={project.name}
                                href={mobileProjectUri(project.id)}
                                context={
                                    project.disabled ? <Tag color="default">Disabled</Tag> : undefined
                                }
                                action={
                                    <MobileFavourite
                                        type="project"
                                        id={project.id}
                                        name={project.name}
                                        favourite={project.favourite}
                                        onToggled={onToggled}
                                    />
                                }
                            />
                        )
                    }
                </MobileEntityGroup>
                {
                    truncated &&
                    <div className="ot-mobile-note" data-testid="mobile-projects-truncated">
                        <Typography.Text type="secondary">
                            {`Showing ${projects.length} of ${total} projects. Filter by name or by label to find another.`}
                        </Typography.Text>
                    </div>
                }
            </MobileAsyncContent>
        </MobileScreen>
    )
}

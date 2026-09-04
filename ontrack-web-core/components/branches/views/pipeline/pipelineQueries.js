import {gql} from "graphql-request";
import {gqlDecorationFragment} from "@components/services/fragments";
import {gqlValidationChipStamp} from "@components/primitives/ValidationChipFragments";

/**
 * The GraphQL the pipeline content view runs.
 *
 * Three documents, matching the three things the view has to know and the three rates at which they
 * change: the branch's own facts (rarely), the page of builds (on filter and on "load more"), and
 * the inspected build (on every selection).
 */

/**
 * The promotion level fields every region of the view needs: the stage cards, the timeline medals
 * and the inspector all draw a level the same way.
 */
export const gqlPipelinePromotionLevel = gql`
    fragment PipelinePromotionLevel on PromotionLevel {
        id
        name
        image
        description
        annotatedDescription
    }
`

/**
 * What the branch itself says, independently of any filter.
 *
 * `allBuilds` is deliberately unfiltered and deliberately NOT the page the timeline shows. The stats
 * row states facts about the branch, and a total which moves when you filter is a readout of the
 * filter, not of the branch.
 *
 * `promotedBuildCount` counts distinct BUILDS, which is what a stage card claims. The number of
 * promotion runs at a level is a different, larger number whenever a build has been promoted again.
 */
export const gqlPipelineBranchFacts = gql`
    query PipelineBranchFacts($branchId: Int!) {
        branch(id: $branchId) {
            id
            allBuilds: buildsPaginated(size: 1) {
                pageInfo {
                    totalSize
                }
                pageItems {
                    id
                    name
                    displayName
                    creation {
                        time
                    }
                }
            }
            promotionLevels {
                ...PipelinePromotionLevel
                promotedBuildCount
                promotionRunsPaginated(size: 1) {
                    pageItems {
                        id
                        creation {
                            time
                        }
                        build {
                            id
                            name
                            displayName
                        }
                    }
                }
            }
            validationStamps {
                id
                name
            }
            scmBranchInfo {
                changeLogs
            }
        }
    }

    ${gqlPipelinePromotionLevel}
`

/**
 * One page of builds for the timeline.
 *
 * Same shape and same generic filter as the legacy builds view runs, so "load more" means the same
 * thing in both views and a build filter selected in one is honoured in the other.
 */
export const gqlPipelineBuilds = gql`
    query PipelineBuilds(
        $branchId: Int!,
        $offset: Int!,
        $size: Int!,
        $filterType: String,
        $filterData: String,
    ) {
        branches(id: $branchId) {
            buildsPaginated(
                offset: $offset,
                size: $size,
                generic: {
                    type: $filterType,
                    data: $filterData
                }
            ) {
                pageInfo {
                    totalSize
                    nextPage {
                        offset
                        size
                    }
                }
                pageItems {
                    id
                    key: id
                    name
                    displayName
                    creation {
                        time
                    }
                    # Core field, so the card shows a build's environments - and anything else an
                    # extension decorates a build with - without this view naming any extension.
                    decorations {
                        ...decorationContent
                    }
                    promotionRuns(lastPerLevel: true) {
                        id
                        creation {
                            time
                            user
                        }
                        promotionLevel {
                            ...PipelinePromotionLevel
                        }
                    }
                    validations {
                        validationStamp {
                            ...ValidationChipStamp
                        }
                        validationRuns(count: 1) {
                            id
                            lastStatus {
                                statusID {
                                    id
                                    name
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ${gqlPipelinePromotionLevel}
    ${gqlValidationChipStamp}
    ${gqlDecorationFragment}
`

/**
 * Everything the inspector shows about the selected build.
 *
 * Fetched per selection rather than carried by the timeline page: the timeline holds many builds and
 * needs only what a card draws, while the inspector holds one build and needs the promotion field
 * values, the run descriptions and the authorizations that go with acting on it.
 */
export const gqlPipelineBuildInspection = gql`
    query PipelineBuildInspection($buildId: Int!) {
        build(id: $buildId) {
            id
            name
            displayName
            authorizations {
                name
                action
                authorized
            }
            branch {
                id
                promotionLevels {
                    ...PipelinePromotionLevel
                }
            }
            promotionRuns {
                id
                creation {
                    time
                    user
                }
                description
                annotatedDescription
                promotionLevel {
                    ...PipelinePromotionLevel
                    fields {
                        name
                        displayName
                        type
                    }
                }
                fieldValues {
                    name
                    value
                }
                authorizations {
                    name
                    action
                    authorized
                }
            }
            validations {
                validationStamp {
                    ...ValidationChipStamp
                }
                validationRuns(count: 1) {
                    id
                    lastStatus {
                        creation {
                            time
                            user
                        }
                        description
                        annotatedDescription
                        statusID {
                            id
                            name
                        }
                    }
                }
            }
        }
    }

    ${gqlPipelinePromotionLevel}
    ${gqlValidationChipStamp}
`

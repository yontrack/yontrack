import {gql} from "graphql-request";

const gqlBuildData = gql`
    fragment BuildData on Build {
        id
        name
        creation {
            time
        }
        branch {
            id
            name
            project {
                id
                name
            }
        }
        promotionRuns(lastPerLevel: true) {
            id
            creation {
                time
            }
            promotionLevel {
                id
                name
                description
                image
            }
        }
        releaseProperty {
            value
        }
    }
`

/**
 * Content of a change log, shared by all the ways to get to a change log (using the IDs
 * of the builds or using their names).
 */
const gqlChangeLogContent = gql`
    fragment ChangeLogContent on SCMChangeLog {
        buildFrom: from {
            ...BuildData
        }
        buildTo: to {
            ...BuildData
        }
        diffLink
        linkChanges {
            project {
                id
                name
            }
            qualifier
            from {
                branch {
                    scmBranchInfo {
                        changeLogs
                    }
                }
                id
                name
                releaseProperty {
                    value
                }
            }
            to {
                id
                name
                releaseProperty {
                    value
                }
            }
        }
        commits {
            commit {
                id
                shortId
                message
                link
                author
                timestamp
            }
            annotatedMessage
            build {
                id
                name
                creation {
                    time
                }
                releaseProperty {
                    value
                }
                promotionRuns(lastPerLevel: true) {
                    creation {
                        time
                    }
                    annotatedDescription
                    description
                    promotionLevel {
                        id
                        name
                        image
                    }
                }
                usingQualified {
                    pageItems {
                        qualifier
                        build {
                            id
                            branch {
                                project {
                                    name
                                }
                            }
                            name
                            releaseProperty {
                                value
                            }
                            creation {
                                time
                            }
                        }
                    }
                }
            }
        }
    }
    ${gqlBuildData}
`

/**
 * Getting a change log using the IDs of its boundaries.
 */
export const gqlChangeLogById = gql`
    query ChangeLog($from: Int!, $to: Int!) {
        scmChangeLog(from: $from, to: $to) {
            ...ChangeLogContent
        }
    }
    ${gqlChangeLogContent}
`

/**
 * Getting a change log using the names (or display names) of its boundaries.
 */
export const gqlChangeLogByName = gql`
    query ChangeLogByName(
        $project: String!,
        $from: String!,
        $to: String!,
        $fromBranch: String,
        $toBranch: String,
    ) {
        scmChangeLogByName(
            project: $project,
            from: $from,
            to: $to,
            fromBranch: $fromBranch,
            toBranch: $toBranch,
        ) {
            ...ChangeLogContent
        }
    }
    ${gqlChangeLogContent}
`

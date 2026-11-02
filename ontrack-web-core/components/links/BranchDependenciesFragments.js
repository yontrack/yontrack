import {gql} from "graphql-request";

const maxDependencyDepth = 5
const maxConsumerDepth = 5

const gqlBuildInfo = gql`
    fragment BuildInfo on Build {
        id
        name
        displayName
        branch {
            id
            name
            project {
                id
                name
            }
            scmBranchInfo {
                changeLogs
            }
        }
        releaseProperty {
            value
        }
        creation {
            time
        }
        promotionRuns(lastPerLevel: true) {
            id
            creation {
                time
            }
            promotionLevel {
                id
                description
                name
                image
            }
        }
    }
`

const gqlBranchInfo = gql`
    fragment BranchInfo on Branch {
        id
        name
        displayName
        project {
            id
            name
        }
    }
`

const gqlBranchNodeInfo = gql`
    fragment BranchNodeInfo on Branch {
        ...BranchInfo
        latestBuilds: builds(count: 1) {
            ...BuildInfo
        }
    }
`

const gqlDependencyLinks = (depth) => {
    if (depth <= 0) {
        return ''
    } else {
        return `
                dependencyLinks(builds: 5) {
                    qualifier
                    sourceBuild {
                        ...BuildInfo
                    }
                    targetBuild {
                        ...BuildInfo
                    }
                    autoVersioning {
                        lastEligibleBuild {
                            ...BuildInfo
                        }
                        status {
                            pullRequest @include(if: $loadPullRequests) {
                                id
                                name
                                link
                                status
                            }
                            order {
                                uuid
                                targetVersion
                            }
                            running
                            mostRecentState {
                              state
                              running
                              processing
                              creation {
                                  time
                              }
                              data
                            }
                        }
                    }
                    branch {
                        ...BranchNodeInfo
                        ${gqlDependencyLinks(depth - 1)}
                    }
                }
            `
    }
}

const gqlConsumerLinks = (depth) => {
    if (depth <= 0) {
        return ''
    } else {
        return `
                consumerLinks(builds: 5) {
                    qualifier
                    sourceBuild {
                        ...BuildInfo
                    }
                    targetBuild {
                        ...BuildInfo
                    }
                    branch {
                        ...BranchNodeInfo
                        ${gqlConsumerLinks(depth - 1)}
                    }
                }
            `
    }
}

export const branchQuery = ({dependencies, consumers}) => `
        query RootBranch($branchId: Int!, $loadPullRequests: Boolean! = false) {
            branch(id: $branchId) {
                ...BranchNodeInfo
                ${dependencies ? gqlDependencyLinks(maxDependencyDepth) : ''}
                ${consumers ? gqlConsumerLinks(maxConsumerDepth) : ''}
            }
        }
        ${gqlBuildInfo}
        ${gqlBranchInfo}
        ${gqlBranchNodeInfo}
    `

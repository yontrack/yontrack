import {gql} from "graphql-request";

const maxDependencyDepth = 5
const maxConsumerDepth = 5

const gqlBuildMinInfo = gql`
    fragment BuildMinInfo on Build {
        id
        name
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

const gqlBuildInfo = gql`
    ${gqlBuildMinInfo}
    fragment BuildInfo on Build {
        ...BuildMinInfo
        branch {
            id
            name
            displayName
            project {
                id
                name
            }
        }
    }
`

const gqlBuildNodeInfo = gql`
    ${gqlBuildInfo}
    fragment BuildNodeInfo on Build {
        ...BuildInfo
        lastBuildInfo: branch {
            lastBuild: builds(count: 1) {
                ...BuildInfo
            }
        }
    }
`

const gqlBuildDependencyLinks = (depth) => {
    if (depth <= 0) {
        return ''
    } else {
        return `
              usingQualified {
                pageItems {
                    qualifier
                    build {
                        ...BuildNodeInfo
                        ${gqlBuildDependencyLinks(depth - 1)}
                    }
                }
              }
            `;
    }
};

const gqlBuildConsumerLinks = (depth) => {
    if (depth <= 0) {
        return ''
    } else {
        return `
              usedByQualified {
                pageItems {
                    qualifier
                    build {
                        ...BuildNodeInfo
                        ${gqlBuildConsumerLinks(depth - 1)}
                    }
                }
              }
            `;
    }
};

// noinspection GraphQLUnresolvedReference
export const buildQuery = `
        query RootBuild($buildId: Int!) {
            build(id: $buildId) {
                ...BuildNodeInfo
                ${gqlBuildDependencyLinks(maxDependencyDepth)}
                ${gqlBuildConsumerLinks(maxConsumerDepth)}
            }
        }
        ${gqlBuildNodeInfo}
    `

// noinspection GraphQLUnresolvedReference
export const buildQueryDependenciesOnly = `
        query RootBuild($buildId: Int!) {
            build(id: $buildId) {
                ...BuildNodeInfo
                ${gqlBuildDependencyLinks(maxDependencyDepth)}
            }
        }
        ${gqlBuildNodeInfo}
    `

export function buildDependencyTreeData(build, qualifier, pathKey = `${build.id}`) {
    return {
        key: pathKey,
        title: build.name,
        qualifier: qualifier,
        build: build,
        children: build.usingQualified ? build.usingQualified.pageItems.map((link, index) =>
            buildDependencyTreeData(link.build, link.qualifier, `${pathKey}:${index}`)
        ) : []
    }
}

import {gql} from "graphql-request";

const maxDownstreamDepth = 5
const maxUpstreamDepth = 5

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

const gqlDownstreamBuildDependencies = (depth) => {
    if (depth <= 0) {
        return ''
    } else {
        return `
              usingQualified {
                pageItems {
                    qualifier
                    build {
                        ...BuildNodeInfo
                        ${gqlDownstreamBuildDependencies(depth - 1)}
                    }
                }
              }
            `;
    }
};

const gqlUpstreamBuildDependencies = (depth) => {
    if (depth <= 0) {
        return ''
    } else {
        return `
              usedByQualified {
                pageItems {
                    qualifier
                    build {
                        ...BuildNodeInfo
                        ${gqlUpstreamBuildDependencies(depth - 1)}
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
                ${gqlDownstreamBuildDependencies(maxDownstreamDepth)}
                ${gqlUpstreamBuildDependencies(maxUpstreamDepth)}
            }
        }
        ${gqlBuildNodeInfo}
    `

// noinspection GraphQLUnresolvedReference
export const buildQueryDownstreamOnly = `
        query RootBuild($buildId: Int!) {
            build(id: $buildId) {
                ...BuildNodeInfo
                ${gqlDownstreamBuildDependencies(maxDownstreamDepth)}
            }
        }
        ${gqlBuildNodeInfo}
    `

function buildDownstreamTreeData(build, qualifier, pathKey = `${build.id}`) {
    return {
        key: pathKey,
        title: build.name,
        qualifier: qualifier,
        build: build,
        children: build.usingQualified ? build.usingQualified.pageItems.map((link, index) =>
            buildDownstreamTreeData(link.build, link.qualifier, `${pathKey}:${index}`)
        ) : []
    }
}

export async function collectDownstreamNodesAsTreeData(client, build) {
    const data = await client.request(buildQueryDownstreamOnly, {buildId: Number(build.id)})
    return buildDownstreamTreeData(data.build)
}
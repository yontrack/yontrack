import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";

/**
 * Creates a validation stamp of the `tests` data type (test summary) on a branch: a test stamp for
 * the test readings of the scorecard.
 */
export const createTestsValidationStamp = async (branch, name) => {
    await graphQLCallMutation(
        branch.ontrack.connection,
        'setupTestSummaryValidationStamp',
        gql`
            mutation SetupTestsValidationStamp($project: String!, $branch: String!, $validation: String!) {
                setupTestSummaryValidationStamp(input: {
                    project: $project,
                    branch: $branch,
                    validation: $validation,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            project: branch.project.name,
            branch: branch.name,
            validation: name,
        }
    )
    return {name, branch}
}

/**
 * Validates a build with a test summary. Any failed test makes a failed run.
 */
export const validateWithTests = async (build, validationStamp, {passed = 0, skipped = 0, failed = 0} = {}) => {
    await graphQLCallMutation(
        build.ontrack.connection,
        'validateBuildWithTests',
        gql`
            mutation ValidateWithTests(
                $project: String!,
                $branch: String!,
                $build: String!,
                $validation: String!,
                $passed: Int!,
                $skipped: Int!,
                $failed: Int!,
            ) {
                validateBuildWithTests(input: {
                    project: $project,
                    branch: $branch,
                    build: $build,
                    validation: $validation,
                    passed: $passed,
                    skipped: $skipped,
                    failed: $failed,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            project: validationStamp.branch.project.name,
            branch: validationStamp.branch.name,
            build: build.name,
            validation: validationStamp.name,
            passed,
            skipped,
            failed,
        }
    )
}

/**
 * Readings of the scorecard of a project, set by set.
 */
export const getProjectScorecard = async (project) => {
    const data = await graphQLCall(
        project.ontrack.connection,
        gql`
            query ProjectScorecard($id: Int!) {
                project(id: $id) {
                    scorecard {
                        sets {
                            name
                            readings {
                                key
                                computedAt
                                value
                                basis
                                unknownReason
                            }
                        }
                    }
                }
            }
        `,
        {id: Number(project.id)}
    )
    return data.project.scorecard
}

/**
 * Queues the recompute of the scorecard of a project and waits for its readings.
 *
 * The recompute is a job: the readings are polled until every set of the project has them, for a
 * minute at most.
 */
export const recomputeScorecardAndWait = async (project, {timeout = 60000} = {}) => {
    await graphQLCallMutation(
        project.ontrack.connection,
        'recomputeProjectScorecard',
        gql`
            mutation RecomputeProjectScorecard($projectId: Int!) {
                recomputeProjectScorecard(input: {projectId: $projectId}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {projectId: Number(project.id)}
    )
    const start = Date.now()
    while (Date.now() - start < timeout) {
        const scorecard = await getProjectScorecard(project)
        if (scorecard.sets.every(set => set.readings.length > 0)) {
            return scorecard
        }
        await new Promise(resolve => setTimeout(resolve, 1000))
    }
    throw new Error(`The scorecard of ${project.name} was not computed within ${timeout} ms.`)
}

/**
 * Whether the licence of the instance allows the estates (`extension.scorecard`).
 */
export const isScorecardLicensed = async (ontrack) => {
    const data = await graphQLCall(
        ontrack.connection,
        gql`
            query LicensedFeatures {
                licenseInfo {
                    license {
                        licensedFeatures {
                            id
                            enabled
                        }
                    }
                }
            }
        `
    )
    return (data.licenseInfo?.license?.licensedFeatures ?? [])
        .some(it => it.id === 'extension.scorecard' && it.enabled)
}

/**
 * Creates an estate.
 *
 * @param ontrack Connection
 * @param name Unique name of the estate
 * @param labels Labels selecting its projects, as `category:name`
 * @param marker Marker, `{kind, levelName}` or `{kind, environment, qualifier}`, default when null
 * @param readings Window and target per reading, `[{key, windowDays, target}]`
 * @param security What the estate expects of the security scans,
 *   `{expectedKinds, freshnessDays, criticalTargetDays, highTargetDays}`, none when null
 * @return The estate, with its `id` and `name`
 */
export const createEstate = async (ontrack, {name, labels, marker = null, readings = [], security = null}) => {
    const data = await graphQLCallMutation(
        ontrack.connection,
        'createEstate',
        gql`
            mutation CreateEstate(
                $name: String!,
                $labels: [String!]!,
                $marker: EstateMarkerInput,
                $readings: [EstateReadingConfigInput!],
                $security: EstateSecurityInput,
            ) {
                createEstate(input: {
                    name: $name,
                    labels: $labels,
                    marker: $marker,
                    readings: $readings,
                    security: $security,
                }) {
                    estate {
                        id
                        name
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        {name, labels, marker, readings, security}
    )
    return data.createEstate.estate
}

/**
 * Deletes an estate — before its labels, whose deletion it would refuse.
 */
export const deleteEstate = async (ontrack, estate) => {
    await graphQLCallMutation(
        ontrack.connection,
        'deleteEstate',
        gql`
            mutation DeleteEstate($id: Int!) {
                deleteEstate(input: {id: $id}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {id: Number(estate.id)}
    )
}

/**
 * Estate by name, `null` if none, with its definition.
 */
export const findEstate = async (ontrack, name) => {
    const data = await graphQLCall(
        ontrack.connection,
        gql`
            query Estate($name: String!) {
                estate(name: $name) {
                    id
                    name
                    description
                    labels {
                        category
                        name
                    }
                    marker {
                        kind
                        levelName
                        environment
                        qualifier
                    }
                    readingConfigs {
                        key
                        windowDays
                        target
                    }
                }
            }
        `,
        {name}
    )
    return data.estate
}

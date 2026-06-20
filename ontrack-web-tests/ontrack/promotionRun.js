import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";

const promotionRunFragment = gql`
    fragment PromotionRunData on PromotionRun {
        id
        description
        build {
            id
            name
        }
        promotionLevel {
            id
            name
        }
    }
`

export const createPromotionRun = async (build, promotionLevel, params) => {

    const promotionLevelName = promotionLevel.name
    const description = params?.description

    const data = await graphQLCallMutation(
        build.ontrack.connection,
        'createValidationRunById',
        gql`
            mutation PromoteBuild(
                $buildId: Int!,
                $promotionLevelName: String!,
                $description: String,
            ) {
                createPromotionRunById(input: {
                    buildId: $buildId,
                    promotion: $promotionLevelName,
                    description: $description,
                }) {
                    promotionRun {
                        ...PromotionRunData
                    }
                    errors {
                        message
                    }
                }
            }

            ${promotionRunFragment}
        `,
        {
            buildId: Number(build.id),
            promotionLevelName,
            description,
        }
    )

    return promotionRunInstance(build.ontrack, build, promotionLevel, data.createPromotionRunById.promotionRun)

}

export const createPromotionRunWithFieldValues = async (build, promotionLevel, fieldValues) => {

    const data = await graphQLCallMutation(
        build.ontrack.connection,
        'createPromotionRunById',
        gql`
            mutation PromoteBuildWithFields(
                $buildId: Int!,
                $promotion: String!,
                $fieldValues: [PromotionRunFieldValueInput!],
            ) {
                createPromotionRunById(input: {
                    buildId: $buildId,
                    promotion: $promotion,
                    fieldValues: $fieldValues,
                }) {
                    promotionRun {
                        ...PromotionRunData
                    }
                    errors {
                        message
                    }
                }
            }

            ${promotionRunFragment}
        `,
        {
            buildId: Number(build.id),
            promotion: promotionLevel.name,
            fieldValues,
        }
    )

    return promotionRunInstance(build.ontrack, build, promotionLevel, data.createPromotionRunById.promotionRun)

}

export const getBuildPromotionRuns = async (build, promotionLevel) => {
    const data = await graphQLCall(
        build.ontrack.connection,
        gql`
            query GetBuildPromotionRuns(
                $buildId: Int!,
                $promotionLevelName: String!,
            ) {
                build(id: $buildId) {
                    promotionRuns(promotion: $promotionLevelName) {
                        ...PromotionRunData
                    }
                }
            }

            ${promotionRunFragment}
        `,
        {
            buildId: Number(build.id),
            promotionLevelName: promotionLevel.name,
        }
    )

    return data.build.promotionRuns.map(it => promotionRunInstance(build.ontrack, build, promotionLevel, it))
}

const promotionRunInstance = (ontrack, build, promotionLevel, data) => {
    const run = {
        ontrack,
        ...data,
        build,
        promotionLevel,
    }

    // TODO Promotion run methods

    return run
}

import {generate} from "@ontrack/utils";
import {graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {registerNotificationExtensions} from "@ontrack/extensions/notifications/notifications";

export const createPromotionLevel = async (branch, name) => {
    const actualName = name ?? generate('pl_')

    const data = await graphQLCallMutation(
        branch.ontrack.connection,
        'createPromotionLevelById',
        gql`
            mutation CreatePromotionLevel(
                $branchId: Int!,
                $name: String!,
            ) {
                createPromotionLevelById(input: {
                    branchId: $branchId,
                    name: $name,
                    description: "",
                }) {
                    promotionLevel {
                        id
                        name
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        {
            branchId: Number(branch.id),
            name: actualName,
        }
    )

    return promotionLevelInstance(branch, data.createPromotionLevelById.promotionLevel)
}


const promotionLevelInstance = (branch, data) => {
    const promotionLevel = {
        ontrack: branch.ontrack,
        type: 'PROMOTION_LEVEL',
        ...data,
        branch,
    }

    // Notifications methods
    registerNotificationExtensions(promotionLevel)

    promotionLevel.setAutoPromotionProperty = async ({validationStamps = [], promotionLevels = [], include = "", exclude = ""} = {}) => {
        await graphQLCallMutation(
            promotionLevel.ontrack.connection,
            'setPromotionLevelAutoPromotionPropertyById',
            gql`
                mutation SetAutoPromotionProperty(
                    $id: Int!,
                    $validationStamps: [String!],
                    $promotionLevels: [String!],
                    $include: String,
                    $exclude: String,
                ) {
                    setPromotionLevelAutoPromotionPropertyById(input: {
                        id: $id,
                        validationStamps: $validationStamps,
                        promotionLevels: $promotionLevels,
                        include: $include,
                        exclude: $exclude,
                    }) {
                        errors { message }
                    }
                }
            `,
            {
                id: Number(promotionLevel.id),
                // the mutation takes names, not IDs
                validationStamps: validationStamps.map(it => it.name ?? it),
                promotionLevels: promotionLevels.map(it => it.name ?? it),
                include,
                exclude,
            }
        )
        return promotionLevel
    }

    promotionLevel.setFields = async (fields) => {
        await graphQLCallMutation(
            promotionLevel.ontrack.connection,
            'setPromotionLevelFields',
            gql`
                mutation SetPromotionLevelFields($promotionLevelId: Int!, $fields: [PromotionLevelFieldInput!]!) {
                    setPromotionLevelFields(input: {
                        promotionLevelId: $promotionLevelId,
                        fields: $fields,
                    }) {
                        errors { message }
                    }
                }
            `,
            {
                promotionLevelId: Number(promotionLevel.id),
                fields,
            }
        )
        return promotionLevel
    }

    return promotionLevel
}
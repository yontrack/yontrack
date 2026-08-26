import {generate} from "@ontrack/utils";
import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
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

    promotionLevel.setAutoPromotionProperty = async ({validationStamps = [], promotionLevels = [], autoRevoke = false} = {}) => {
        await graphQLCallMutation(
            promotionLevel.ontrack.connection,
            'setPromotionLevelAutoPromotionPropertyById',
            gql`
                mutation SetAutoPromotionProperty(
                    $id: Int!,
                    $validationStamps: [String!],
                    $promotionLevels: [String!],
                    $autoRevoke: Boolean,
                ) {
                    setPromotionLevelAutoPromotionPropertyById(input: {
                        id: $id,
                        validationStamps: $validationStamps,
                        promotionLevels: $promotionLevels,
                        autoRevoke: $autoRevoke,
                    }) {
                        errors { message }
                    }
                }
            `,
            {
                id: Number(promotionLevel.id),
                // the mutation takes names, not IDs
                validationStamps: validationStamps.map(it => it.name),
                promotionLevels: promotionLevels.map(it => it.name),
                autoRevoke,
            }
        )
        return promotionLevel
    }

    promotionLevel.getAutoPromotionProperty = async () => {
        const data = await graphQLCall(
            promotionLevel.ontrack.connection,
            gql`
                query GetAutoPromotionProperty($id: Int!) {
                    promotionLevel(id: $id) {
                        properties {
                            type { typeName }
                            value
                        }
                    }
                }
            `,
            {id: Number(promotionLevel.id)}
        )
        return data.promotionLevel.properties.find(
            p => p.type.typeName === 'net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType'
        )?.value
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
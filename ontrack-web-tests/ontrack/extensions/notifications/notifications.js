import {graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";

export const registerNotificationExtensions = (projectEntity) => {
    projectEntity.subscribe = async ({name, events, keywords, channel, channelConfig, contentTemplate}) => {
        await graphQLCallMutation(
            projectEntity.ontrack.connection,
            'subscribeToEvents',
            gql`
                mutation CreateSubscription(
                    $name: String!,
                    $entityType: ProjectEntityType!,
                    $entityId: Int!,
                    $events: [String!]!,
                    $keywords: String,
                    $channel: String!,
                    $channelConfig: JSON!,
                    $contentTemplate: String,
                ) {
                    subscribeToEvents(input: {
                        name: $name,
                        projectEntity: {
                            type: $entityType,
                            id: $entityId,
                        },
                        events: $events,
                        keywords: $keywords,
                        channel: $channel,
                        channelConfig: $channelConfig,
                        contentTemplate: $contentTemplate,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {
                name,
                entityType: projectEntity.type,
                entityId: Number(projectEntity.id),
                events,
                keywords,
                channel,
                channelConfig,
                contentTemplate,
            }
        )
    }
}
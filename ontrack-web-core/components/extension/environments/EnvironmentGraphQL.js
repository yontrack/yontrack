import {gql} from "graphql-request";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";

export const gqlEnvironmentData = gql`
    fragment EnvironmentData on Environment {
        id
        name
        description
        order
        tags
    }
`;

export const gqlSlotDataNoProject = gql`
    fragment SlotDataNoProject on Slot {
        id
        qualifier
        description
        environment {
            id
            name
            order
            image
            tags
        }
    }
`;

export const gqlSlotData = gql`
    fragment SlotData on Slot {
        ...SlotDataNoProject
        project {
            ...ProjectContent
        }
    }
    ${gqlProjectContentFragment}
    ${gqlSlotDataNoProject}
`;

export const gqlSlotPipelineBuildData = gql`
    fragment SlotPipelineBuildData on Build {
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
        displayName
    }
`;

export const gqlSlotPipelineDataNoBuild = gql`
    fragment SlotPipelineDataNoBuild on SlotPipeline {
        id
        number
        start
        end
        status
        finished
        errorMessage
        lastChange {
            message
        }
    }
`;

export const gqlSlotPipelineData = gql`
    fragment SlotPipelineData on SlotPipeline {
        ...SlotPipelineDataNoBuild
        build {
            ...SlotPipelineBuildData
        }
    }
    ${gqlSlotPipelineBuildData}
    ${gqlSlotPipelineDataNoBuild}
`;

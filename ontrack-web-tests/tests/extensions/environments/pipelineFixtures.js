import {graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";

export const createPipeline = async ({
                                         project,
                                         slot,
                                         forceDone = false,
                                         forceDoneMessage = '',
                                         branchSetup,
                                     }) => {
    const branch = await project.createBranch("main")

    if (branchSetup) {
        await branchSetup(branch)
    }

    const build = await branch.createBuild()

    const data = await graphQLCallMutation(
        slot.ontrack.connection,
        '',
        gql`
            mutation CreatePipeline(
                $slotId: String!,
                $buildId: Int!,
                $forceDone: Boolean!,
                $forceDoneMessage: String,
            ) {
                startSlotPipeline(input: {
                    slotId: $slotId,
                    buildId: $buildId,
                    forceDone: $forceDone,
                    forceDoneMessage: $forceDoneMessage,
                }) {
                    pipeline {
                        id
                        number
                        slot {
                            id
                            project {
                                id
                                name
                            }
                            qualifier
                            environment {
                                id
                                name
                            }
                        }
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        {
            slotId: slot.id,
            buildId: Number(build.id),
            forceDone,
            forceDoneMessage,
        }
    )

    return {
        pipeline: data.startSlotPipeline.pipeline,
        // The branch and the build this fixture created along the way: a caller which wants to look
        // at the deployment from somewhere else in the UI needs to name them
        branch,
        build,
    }
}
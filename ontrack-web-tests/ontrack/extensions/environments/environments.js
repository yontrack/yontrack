import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {generate} from "@ontrack/utils";

export class EnvironmentsExtension {
    constructor(ontrack) {
        this.ontrack = ontrack
    }

    async createEnvironment({name, description, order, tags}) {
        const actualName = name ?? generate('env-')
        const actualDescription = description ?? ''
        const actualOrder = order ?? 1
        const actualTags = tags ?? []
        const data = await graphQLCallMutation(
            this.ontrack.connection,
            'createEnvironment',
            gql`
                mutation CreateEnvironment(
                    $name: String!,
                    $description: String!,
                    $order: Int!,
                    $tags: [String!]!,
                ) {
                    createEnvironment(input: {
                        name: $name,
                        description: $description,
                        order: $order,
                        tags: $tags,
                    }) {
                        errors {
                            message
                        }
                        environment {
                            ...EnvironmentData
                        }
                    }
                }
                ${gqlEnvironmentData}
            `,
            {
                name: actualName,
                description: actualDescription,
                order: actualOrder,
                tags: actualTags,
            }
        )

        const environment = {
            ...data.createEnvironment.environment,
            ontrack: this.ontrack
        }

        environment.createSlot = async ({project}) => {
            return this.createSlot({project, environment})
        }

        return environment
    }

    async findEnvironmentByName(name) {
        const data = await graphQLCall(
            this.ontrack.connection,
            gql`
                query FindEnvironmentByName($name: String!) {
                    environmentByName(name: $name) {
                        ...EnvironmentData
                    }
                }
                ${gqlEnvironmentData}
            `,
            {name}
        )
        return data?.environmentByName
    }

    async createSlot({project, qualifier = "", environment}) {
        const data = await graphQLCallMutation(
            this.ontrack.connection,
            'createSlots',
            gql`
                mutation CreateSlot(
                    $projectId: Int!,
                    $qualifier: String!,
                    $description: String!,
                    $environmentId: String!,
                ) {
                    createSlots(input: {
                        projectId: $projectId,
                        qualifier: $qualifier,
                        description: $description,
                        environmentIds: [$environmentId]
                    }) {
                        slots {
                            slots {
                                ...SlotData
                            }
                        }
                        errors {
                            message
                        }
                    }
                }
                ${gqlSlotData}
            `,
            {
                projectId: Number(project.id),
                qualifier: qualifier,
                description: "",
                environmentId: environment.id,
            }
        )
        const slot = data.createSlots.slots.slots[0]
        slot.ontrack = this.ontrack
        slot.createPipeline = async ({build}) => {
            return await this.createPipeline({slot, build});
        }
        return slot
    }

    async createPipeline({slot, build}) {
        const data = await graphQLCallMutation(
            this.ontrack.connection,
            'startSlotPipeline',
            gql`
                mutation CreatePipeline(
                    $slotId: String!,
                    $buildId: Int!,
                ) {
                    startSlotPipeline(input: {
                        slotId: $slotId,
                        buildId: $buildId,
                    }) {
                        pipeline {
                            ...PipelineData
                        }
                        errors {
                            message
                        }
                    }
                }
                ${gqlPipelineData}
            `,
            {
                slotId: slot.id,
                buildId: Number(build.id),
            }
        )

        const pipeline = data.startSlotPipeline.pipeline
        pipeline.ontrack = this.ontrack
        return pipeline
    }

    async startPipeline({pipeline}) {
        const data = await graphQLCallMutation(
            this.ontrack.connection,
            'startSlotPipelineDeployment',
            gql`
                mutation StartPipeline(
                    $pipelineId: String!,
                ) {
                    startSlotPipelineDeployment(input: {
                        pipelineId: $pipelineId,
                    }) {
                        errors {
                            message
                        }
                        deploymentStatus {
                            ok
                        }
                    }
                }
            `,
            {
                pipelineId: pipeline.id,
            }
        )

        const status = data.startSlotPipelineDeployment.deploymentStatus?.ok
        if (!status) {
            throw new Error("Cannot deploy pipeline")
        }
    }

    /**
     * Takes a running pipeline all the way to DONE.
     *
     * The counterpart of `startPipeline`, and the only way to arrange a build
     * that is *currently deployed*: `Build.currentDeployments` is the last
     * DEPLOYED pipeline of each of the project's slots, so a pipeline stopping
     * at RUNNING leaves the build deployed nowhere. Everything that reads a
     * deployment badge needs a build that got this far.
     *
     * Every other spec so far has finished a pipeline by clicking the desktop
     * UI's own button, which is fine when the desktop pipeline page is the
     * subject and useless when it is only the fixture.
     *
     * `forcing: false` is sent although the Kotlin input declares that default:
     * the generated GraphQL input type makes every non-nullable field required
     * whatever the Kotlin side defaults it to, so omitting it fails validation.
     */
    async finishPipeline({pipeline}) {
        const data = await graphQLCallMutation(
            this.ontrack.connection,
            'finishSlotPipelineDeployment',
            gql`
                mutation FinishPipeline(
                    $pipelineId: String!,
                ) {
                    finishSlotPipelineDeployment(input: {
                        pipelineId: $pipelineId,
                        forcing: false,
                    }) {
                        errors {
                            message
                        }
                        finishStatus {
                            ok
                        }
                    }
                }
            `,
            {
                pipelineId: pipeline.id,
            }
        )

        const status = data.finishSlotPipelineDeployment.finishStatus?.ok
        if (!status) {
            throw new Error("Cannot finish pipeline")
        }
    }

    async addAdmissionRule({slot, description = "", ruleId, ruleConfig}) {
        const data = await graphQLCallMutation(
            this.ontrack.connection,
            'saveSlotAdmissionRuleConfig',
            gql`
                mutation CreateAdmissionRule(
                    $slotId: String!,
                    $description: String!,
                    $ruleId: String!,
                    $ruleConfig: JSON!,
                ) {
                    saveSlotAdmissionRuleConfig(input: {
                        slotId: $slotId,
                        description: $description,
                        ruleId: $ruleId,
                        ruleConfig: $ruleConfig,
                    }) {
                        admissionRuleConfig {
                            id
                        }
                        errors {
                            message
                        }
                    }
                }
            `,
            {
                slotId: slot.id,
                description,
                ruleId,
                ruleConfig,
            }
        )
        return data.saveSlotAdmissionRuleConfig.admissionRuleConfig.id
    }

    async addManualApproval({slot}) {
        return await this.addAdmissionRule({
            slot,
            ruleId: "manual",
            ruleConfig: {
                message: "Approval message",
            }
        })
    }

    async addPromotionRule({slot, promotion = "GOLD"}) {
        return await this.addAdmissionRule({
            slot,
            ruleId: "promotion",
            ruleConfig: {
                promotion: promotion,
            }
        })
    }
}

const gqlEnvironmentData = gql`
    fragment EnvironmentData on Environment {
        id
        name
        description
        order
        tags
    }
`

export const gqlSlotData = gql`
    fragment SlotData on Slot {
        id
        description
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
`

export const gqlPipelineData = gql`
    fragment PipelineData on SlotPipeline {
        id
        number
        status
        slot {
            ...SlotData
        }
    }
    ${gqlSlotData}
`

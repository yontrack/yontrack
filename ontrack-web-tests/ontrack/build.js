import {generate} from "@ontrack/utils";
import {graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {createValidationRun} from "@ontrack/validationRun";
import {createBuildLink} from "@ontrack/links";
import {createPromotionRun, createPromotionRunWithFieldValues, getBuildPromotionRuns} from "@ontrack/promotionRun";
import {setReleaseProperty} from "@ontrack/extensions/general/release";

export const createBuild = async (branch, name) => {
    const actualName = name ?? generate('b_')

    const data = await graphQLCallMutation(
        branch.ontrack.connection,
        'createBuild',
        gql`
            mutation CreateBuild(
                $branchId: Int!,
                $name: String!,
            ) {
                createBuild(input: {
                    branchId: $branchId,
                    name: $name,
                }) {
                    build {
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

    return buildInstance(branch, data.createBuild.build)
}


const buildInstance = (branch, data) => {
    const build = {
        ontrack: branch.ontrack,
        ...data,
        branch,
    }

    build.setRelease = async (release) => setReleaseProperty(build, release)

    build.promote = async (promotionLevel, config) => createPromotionRun(build, promotionLevel, config)
    build.promoteWithFieldValues = async (promotionLevel, fieldValues) => createPromotionRunWithFieldValues(build, promotionLevel, fieldValues)
    build.validate = async (validationStamp, config) => createValidationRun(build, validationStamp, config)
    build.linkTo = async (other, qualifier = "") => createBuildLink(build, other, qualifier)

    build.autoVersioningCheck = async () => build.ontrack.autoVersioning.autoVersioningCheck(build)

    build.promotionRuns = async (promotionLevel) => getBuildPromotionRuns(build, promotionLevel)

    return build
}
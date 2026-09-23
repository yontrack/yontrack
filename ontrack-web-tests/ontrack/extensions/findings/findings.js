import {graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {generate} from "@ontrack/utils";

export const FINDINGS_VALIDATION_DATA_TYPE = "net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType"

/**
 * Creates a validation stamp of the `security-findings` data type on a branch.
 */
export const createFindingsValidationStamp = async (branch, name) => {
    const actualName = name ?? generate('vs_security_')
    const data = await graphQLCallMutation(
        branch.ontrack.connection,
        'createValidationStampById',
        gql`
            mutation CreateFindingsValidationStamp(
                $branchId: Int!,
                $name: String!,
                $dataType: String!,
                $dataTypeConfig: JSON!,
            ) {
                createValidationStampById(input: {
                    branchId: $branchId,
                    name: $name,
                    description: "",
                    dataType: $dataType,
                    dataTypeConfig: $dataTypeConfig,
                }) {
                    validationStamp {
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
            dataType: FINDINGS_VALIDATION_DATA_TYPE,
            dataTypeConfig: {
                warningLevel: "HIGH",
                warningValue: 1,
                failedLevel: "CRITICAL",
                failedValue: 1,
            },
        }
    )
    return {
        ...data.createValidationStampById.validationStamp,
        branch,
    }
}

/**
 * One finding of a report in the neutral format.
 */
export const finding = ({
                            externalId,
                            severity = "HIGH",
                            location = "pkg:maven/org.x/y",
                            title,
                            acceptedUntil,
                        }) => ({
    externalId,
    location,
    severity,
    title: title ?? `Title of ${externalId}`,
    ...(acceptedUntil ? {
        acceptance: {
            statement: "Not reachable",
            expiresAt: acceptedUntil,
            source: ".trivyignore.yaml",
        }
    } : {}),
})

/**
 * Validates a build with a report of security scan in the neutral format, creating the build.
 *
 * @param branch Branch to create the build in
 * @param validationStamp `security-findings` validation stamp
 * @param findings Findings of the report, see `finding`
 * @param scanner Name of the scanner
 * @param kind Kind of scan
 * @return The created build
 */
export const scanWithFindings = async (branch, validationStamp, findings, {scanner = "trivy", kind = "IMAGE"} = {}) => {
    const build = await branch.createBuild()
    await graphQLCallMutation(
        branch.ontrack.connection,
        'validateBuildWithFindings',
        gql`
            mutation ValidateBuildWithFindings(
                $project: String!,
                $branch: String!,
                $build: String!,
                $validation: String!,
                $report: JSON!,
            ) {
                validateBuildWithFindings(input: {
                    project: $project,
                    branch: $branch,
                    build: $build,
                    validation: $validation,
                    format: "findings",
                    report: $report,
                }) {
                    validationRun {
                        id
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        {
            project: branch.project.name,
            branch: branch.name,
            build: build.name,
            validation: validationStamp.name,
            report: {scanner, kind, findings},
        }
    )
    return build
}

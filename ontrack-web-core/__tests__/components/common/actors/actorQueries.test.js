/**
 * @jest-environment node
 */
import fs from "fs"
import path from "path"
import {buildSchema, parse, validate} from "graphql"
import {gqlSignatureActorFields} from "@components/common/actors/actors"
import {gqlAssistedChangeFields} from "@components/extension/scm/assistants/assistants"
import {gqlBuilds} from "@components/branches/branchQueries"
import {
    gqlValidationRunContent,
    gqlValidationRunTableContent,
} from "@components/validationRuns/ValidationRunGraphQLFragments"
import {gqlDeploymentPage} from "@components/extension/environments/deployment/deploymentGraphQL"

/**
 * The selections behind the badges of agents (#2032), checked against the schema: a mistyped field
 * fails the whole document, and the page then shows no build at all rather than no badge.
 */
describe('the selections of the actor and of the assisted change', () => {

    const schema = buildSchema(
        fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
    )

    const check = (name, document) => {
        it(`${name} is valid`, () => {
            const errors = validate(schema, parse(document))
            expect(errors.map(error => error.message)).toEqual([])
        })
    }

    check('the actor of a signature', `
        query Build($id: Int!) {
            build(id: $id) {
                creation { user time ${gqlSignatureActorFields} }
            }
        }
    `)

    check('the assisted change of a build', `
        query Build($id: Int!) {
            build(id: $id) {
                ${gqlAssistedChangeFields}
            }
        }
    `)

    check('the builds of the branch page', gqlBuilds)

    check('the validation runs and their statuses', `
        query Runs($id: Int!) {
            validationRuns(id: $id) {
                ...ValidationRunContent
                ...ValidationRunTableContent
            }
        }
        ${gqlValidationRunContent}
        ${gqlValidationRunTableContent}
    `)

    check('the deployment page', gqlDeploymentPage)
})

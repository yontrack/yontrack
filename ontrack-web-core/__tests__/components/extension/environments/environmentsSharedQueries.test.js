import fs from "fs"
import path from "path"
import {buildSchema, parse, validate} from "graphql"

import {
    gqlBuildJourneyData,
    gqlDeployDialogBuilds,
    gqlDeployDialogSlots,
    gqlDeployDialogStart,
    gqlSlotCellData,
    gqlSlotDrawer,
    gqlSlotCancel,
    gqlSlotDrawerDeployment,
    gqlSlotDrawerFinish,
    gqlSlotDrawerRun,
    gqlSlotDrawerTitle,
    gqlSharedAdmissionRuleData,
} from "@components/extension/environments/shared/environmentsSharedGraphQL"
import {gqlDeploymentPage} from "@components/extension/environments/deployment/deploymentGraphQL"
import {gqlSlotDeployments, gqlSlotPage} from "@components/extension/environments/slot/slotGraphQL"
import {gqlSetup} from "@components/extension/environments/setup/setupGraphQL"
import {gqlSlotData} from "@components/extension/environments/EnvironmentGraphQL"

/**
 * The shared components' documents, checked against the schema.
 *
 * Worth checking here rather than in a browser because the failure mode is a bad one: a mistyped
 * field makes the *whole* document fail, so the drawer falls back to its error state and the dialog
 * to "no slot" - and "this project has no deployment slot" is a plausible thing for a project, and
 * therefore reads as data rather than as a defect.
 *
 * `ontrack.graphql` is generated from a running instance (`scripts/dev-graphql-schema.sh`), so this
 * also fails if a new server-side field is added without the schema being regenerated - which is
 * the other half of the same mistake.
 */
describe('the shared environments documents', () => {

    const schema = buildSchema(
        fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
    )

    const check = (name, document) => {
        it(`${name} is valid`, () => {
            const errors = validate(schema, parse(document))
            expect(errors.map(error => error.message)).toEqual([])
        })
    }

    // Fragments cannot be validated on their own - an unused fragment is an error - so each is
    // checked through a query that spreads it.
    check('the slot cell fragment', `
        query CheckSlotCell($id: String!) {
            slotById(id: $id) { ...SlotCellData }
        }
        ${gqlSlotCellData}
    `)

    check('the build journey fragment', `
        query CheckBuildJourney($id: Int!) {
            build(id: $id) { journey { ...BuildJourneyData } }
        }
        ${gqlBuildJourneyData}
    `)

    // Two fragments selecting `environment` and `project` with different subsets are where the two
    // could collide, so one document spreads both. The `environments` query is the one still shaped
    // that way now that the home page is the matrix (#1791).
    check('the environments query, with the cell fragment beside the slot one', `
        query CheckEnvironmentList($filterProjects: [String!], $filterTags: [String!]) {
            environments(filter: {projects: $filterProjects, tags: $filterTags}) {
                id
                slots(projects: $filterProjects) {
                    ...SlotData
                    ...SlotCellData
                }
            }
        }
        ${gqlSlotData}
        ${gqlSlotCellData}
    `)

    check('the drawer title query', gqlSlotDrawerTitle)
    check('the drawer query', gqlSlotDrawer)
    check('the drawer deployment query', gqlSlotDrawerDeployment)
    check('the drawer run mutation', gqlSlotDrawerRun)
    check('the drawer finish mutation', gqlSlotDrawerFinish)
    check('the cancel mutation', gqlSlotCancel)
    check('the deploy dialog slots query', gqlDeployDialogSlots)
    check('the deploy dialog builds query', gqlDeployDialogBuilds)
    check('the deploy dialog start mutation', gqlDeployDialogStart)
    check('the deployment page query', gqlDeploymentPage)
    check('the slot page query', gqlSlotPage)
    check('the slot deployments query', gqlSlotDeployments)
    check('the Setup page query', gqlSetup)

    it('asks the slot whether it is blocked and whether it is behind', () => {
        // The two flags the cell exists to carry; without them it draws neither mark and a held-up
        // deployment looks like a quiet one.
        expect(gqlSlotCellData).toMatch(/^\s+blocked$/m)
        expect(gqlSlotCellData).toMatch(/^\s+behind$/m)
    })

    it('asks for the in-flight deployment as well as the deployed one', () => {
        // Two different builds, and the cell must never show the second in place of the first.
        expect(gqlSlotCellData).toContain('lastDeployedPipeline')
        expect(gqlSlotCellData).toContain('currentPipeline')
    })

    it('asks each refusing rule for its configuration, not only its name', () => {
        // The rule components phrase the refusal from its config - "GOLD promotion is required" -
        // and a name alone leaves the dialog with nothing to say.
        expect(gqlDeployDialogSlots).toContain('ruleConfig')
        expect(gqlBuildJourneyData).toContain('ruleConfig')
    })

    it('asks the slot for the deployment a new one would cancel', () => {
        expect(gqlDeployDialogSlots).toContain('currentPipeline')
        expect(gqlDeployDialogBuilds).toContain('currentPipeline')
    })

    it('asks the slot for its authorizations, which is what hides the actions', () => {
        expect(gqlDeployDialogSlots).toContain('authorizations')
        expect(gqlDeployDialogBuilds).toContain('authorizations')
        expect(gqlSlotDrawer).toContain('authorizations')
        expect(gqlDeploymentPage).toContain('authorizations')
        // The slot page hides its whole Setup tab on `slot.edit`.
        expect(gqlSlotPage).toContain('authorizations')
    })

    it('asks each admission rule for the answer given to it', () => {
        // Who approved, and what they wrote. It is what lets the manual approval's `Check` component
        // say more than "this must be approved manually" - see #1793.
        expect(gqlSharedAdmissionRuleData).toMatch(/data\s*{[^}]*user/)
    })

    it('sends every deployments filter to the server', () => {
        // Filtering the page that came back instead would make the answer depend on how much of the
        // history happened to be on it.
        expect(gqlSlotDeployments).toContain('status: $status')
        expect(gqlSlotDeployments).toContain('buildName: $buildName')
        expect(gqlSlotDeployments).toContain('user: $user')
    })

    it('asks the deployment for the three phases of workflows, not only the current one', () => {
        // A finished deployment shows all three, read-only; asking for the current phase alone
        // would leave a page whose record has its middle missing (#1792).
        expect(gqlDeploymentPage).toContain('candidateWorkflows')
        expect(gqlDeploymentPage).toContain('runningWorkflows')
        expect(gqlDeploymentPage).toContain('doneWorkflows')
    })

    it('asks the deployment for its changes, which are the audit timeline', () => {
        // Including `overrideMessage`: the justification somebody wrote is the one thing the old
        // page never showed, and the timeline is the only place it now appears.
        expect(gqlDeploymentPage).toContain('changes')
        expect(gqlDeploymentPage).toContain('overrideMessage')
    })
})

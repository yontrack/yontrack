import {slotRows} from "@components/extension/environments/setup/SetupSlotsTab"

/**
 * The Setup page's Slots tab turns the server's answer inside out: it comes back by environment,
 * because that is how slots are stored, and the tab groups by project, because that is the question
 * somebody brings to it.
 */
describe('slotRows', () => {

    const staging = {id: 'env-staging', name: 'staging', order: 10}
    const production = {id: 'env-production', name: 'production', order: 20}

    const petclinic = {id: 1, name: 'petclinic'}
    const acme = {id: 2, name: 'acme'}

    const slot = (id, project, {qualifier = '', rules = 0, workflows = 0} = {}) => ({
        id,
        qualifier,
        project,
        admissionRules: Array.from({length: rules}, (_, i) => ({id: `r-${id}-${i}`})),
        workflows: Array.from({length: workflows}, (_, i) => ({id: `w-${id}-${i}`})),
    })

    const environments = [
        {...staging, slots: [slot('s1', petclinic, {rules: 2}), slot('s2', acme)]},
        {
            ...production,
            slots: [
                slot('s3', petclinic, {rules: 1, workflows: 3}),
                slot('s4', petclinic, {qualifier: 'canary'}),
            ],
        },
    ]

    it('groups by project and then orders by environment', () => {
        expect(slotRows(environments).map(row => [row.project.name, row.environment.name, row.qualifier]))
            .toEqual([
                ['acme', 'staging', ''],
                ['petclinic', 'staging', ''],
                ['petclinic', 'production', ''],
                ['petclinic', 'production', 'canary'],
            ])
    })

    it('counts the rules and workflows of each slot', () => {
        const rows = slotRows(environments)
        const s3 = rows.find(row => row.id === 's3')
        expect(s3.rules).toEqual(1)
        expect(s3.workflows).toEqual(3)
        const s2 = rows.find(row => row.id === 's2')
        expect(s2.rules).toEqual(0)
        expect(s2.workflows).toEqual(0)
    })

    it('gives the first row of each project the whole span and the rest none', () => {
        // Ant Design draws a cell whose `rowSpan` is 0 not at all, which is what merges the project
        // column down its group.
        expect(slotRows(environments).map(row => row.projectRowSpan)).toEqual([1, 3, 0, 0])
    })

    it('has no rows when nothing is configured', () => {
        expect(slotRows([])).toEqual([])
        expect(slotRows(undefined)).toEqual([])
        expect(slotRows([{...staging, slots: []}])).toEqual([])
    })
})

import fs from "fs"
import path from "path"
import {buildSchema, parse, validate} from "graphql"

import {
    defaultExpandedKeys,
    defaultMatrixFilter,
    filterFromQuery,
    filterToInput,
    filterToQuery,
    groupEnvironments,
    isFiltered,
    labelName,
    matrixRows,
    projectRowKey,
    SCOPE_ALL,
    SCOPE_FAVOURITES,
    slotAt,
} from "@components/extension/environments/matrix/environmentMatrixModel"
import {
    gqlEnvironmentMatrix,
    gqlEnvironmentsCount,
    gqlEnvironmentTags,
    gqlProjectLabels,
} from "@components/extension/environments/matrix/environmentMatrixGraphQL"

const environment = (id, name, order, tags = []) => ({id, name, order, tags})
const slot = (id, environmentId) => ({id, environment: {id: environmentId}})

describe('the matrix documents', () => {

    const schema = buildSchema(
        fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
    )

    const check = (name, document) => {
        it(`${name} is valid`, () => {
            const errors = validate(schema, parse(document))
            expect(errors.map(error => error.message)).toEqual([])
        })
    }

    check('the matrix query', gqlEnvironmentMatrix)
    check('the environments count query', gqlEnvironmentsCount)
    check('the environment tags query', gqlEnvironmentTags)
    check('the project labels query', gqlProjectLabels)
})

describe('the matrix filter', () => {

    it('opens on favourites, because the first question is "mine"', () => {
        expect(defaultMatrixFilter().scope).toBe(SCOPE_FAVOURITES)
    })

    it('reads the URL over the stored preference', () => {
        const stored = {project: 'stored', scope: SCOPE_FAVOURITES, label: 7, tags: ['a'], activity: true}
        const filter = filterFromQuery({project: 'url', scope: 'all'}, stored)
        expect(filter.project).toBe('url')
        expect(filter.scope).toBe(SCOPE_ALL)
        // ...and keeps the stored value of everything the URL says nothing about
        expect(filter.label).toBe(7)
        expect(filter.tags).toEqual(['a'])
        expect(filter.activity).toBe(true)
    })

    it('falls back to the stored preference, then to the defaults', () => {
        expect(filterFromQuery({}, {scope: SCOPE_ALL}).scope).toBe(SCOPE_ALL)
        expect(filterFromQuery({}, null)).toEqual(defaultMatrixFilter())
    })

    it('ignores a scope the URL made up', () => {
        expect(filterFromQuery({scope: 'nonsense'}, null).scope).toBe(SCOPE_FAVOURITES)
    })

    it('reads a repeated parameter as its first value', () => {
        // A query parameter repeated in the URL arrives as an array
        expect(filterFromQuery({project: ['first', 'second']}, null).project).toBe('first')
    })

    it('reads the tags as a comma-separated list', () => {
        expect(filterFromQuery({tags: 'production,canary'}, null).tags).toEqual(['production', 'canary'])
        expect(filterFromQuery({tags: ''}, null).tags).toEqual([])
    })

    it('writes only what is not the default, so an unfiltered matrix has a bare URL', () => {
        expect(filterToQuery(defaultMatrixFilter())).toEqual({})
        expect(filterToQuery({...defaultMatrixFilter(), scope: SCOPE_ALL})).toEqual({scope: 'all'})
        expect(filterToQuery({
            project: 'pet',
            scope: SCOPE_ALL,
            label: 3,
            tags: ['production'],
            activity: true,
        })).toEqual({
            project: 'pet',
            scope: 'all',
            label: '3',
            tags: 'production',
            activity: 'true',
        })
    })

    it('survives a round trip through the URL', () => {
        const filter = {project: 'pet', scope: SCOPE_ALL, label: 3, tags: ['a', 'b'], activity: true}
        expect(filterFromQuery(filterToQuery(filter), null)).toEqual(filter)
    })

    it('turns the Favourites/All scope into the server-side boolean', () => {
        expect(filterToInput(defaultMatrixFilter()).favourites).toBe(true)
        expect(filterToInput({...defaultMatrixFilter(), scope: SCOPE_ALL}).favourites).toBe(false)
    })

    it('sends no project fragment rather than an empty one', () => {
        // An empty string would be `ILIKE '%%'`, which is true of everything but is still a criterion
        expect(filterToInput(defaultMatrixFilter()).project).toBeNull()
    })

    it('carries a widget pin through untouched', () => {
        const input = filterToInput({
            ...defaultMatrixFilter(),
            projects: ['petclinic'],
            environments: ['production'],
        })
        expect(input.projects).toEqual(['petclinic'])
        expect(input.environments).toEqual(['production'])
    })

    it('counts sitting on Favourites as being filtered', () => {
        // The commonest reason an instance full of environments shows an empty matrix
        expect(isFiltered(defaultMatrixFilter())).toBe(true)
        expect(isFiltered({...defaultMatrixFilter(), scope: SCOPE_ALL})).toBe(false)
        expect(isFiltered({...defaultMatrixFilter(), scope: SCOPE_ALL, tags: ['a']})).toBe(true)
    })
})

describe('the matrix columns', () => {

    it('groups consecutive environments under their first tag', () => {
        const groups = groupEnvironments([
            environment('1', 'dev', 100, ['non-production']),
            environment('2', 'staging', 200, ['non-production', 'other']),
            environment('3', 'production', 300, ['production']),
        ])
        expect(groups.map(g => g.tag)).toEqual(['non-production', 'production'])
        expect(groups[0].environments.map(e => e.name)).toEqual(['dev', 'staging'])
        expect(groups[1].environments.map(e => e.name)).toEqual(['production'])
    })

    it('leaves an untagged environment in a group with no heading', () => {
        const groups = groupEnvironments([
            environment('1', 'dev', 100),
            environment('2', 'production', 200, ['production']),
        ])
        expect(groups[0].tag).toBeNull()
        expect(groups[1].tag).toBe('production')
    })

    it('never reorders the environments to group them', () => {
        // The columns read left to right as a delivery pipeline; grouping must not disturb that,
        // so a tag reappearing later is a second group rather than a reason to move a column.
        const groups = groupEnvironments([
            environment('1', 'dev', 100, ['x']),
            environment('2', 'staging', 200, ['y']),
            environment('3', 'preprod', 300, ['x']),
        ])
        expect(groups.map(g => g.tag)).toEqual(['x', 'y', 'x'])
    })

    it('finds the slot of a row in an environment, and nothing where there is none', () => {
        const row = {slots: [slot('s1', 'e1'), slot('s2', 'e2')]}
        expect(slotAt(row, 'e2').id).toBe('s2')
        expect(slotAt(row, 'e3')).toBeNull()
        expect(slotAt(null, 'e1')).toBeNull()
    })
})

describe('the matrix rows', () => {

    const project = {id: 1, name: 'petclinic'}

    it('draws a project with only the default qualifier as one flat row', () => {
        const rows = matrixRows([
            {project, rows: [{qualifier: '', slots: [slot('s1', 'e1')]}]},
        ])
        expect(rows).toHaveLength(1)
        expect(rows[0].children).toBeUndefined()
        expect(rows[0].slots.map(s => s.id)).toEqual(['s1'])
    })

    it('nests the other qualifiers under the project row', () => {
        const rows = matrixRows([
            {
                project,
                rows: [
                    {qualifier: '', slots: [slot('s1', 'e1')]},
                    {qualifier: 'canary', slots: [slot('s2', 'e2')]},
                ],
            },
        ])
        expect(rows[0].key).toBe(projectRowKey(project))
        expect(rows[0].slots.map(s => s.id)).toEqual(['s1'])
        expect(rows[0].children).toHaveLength(1)
        expect(rows[0].children[0].qualifier).toBe('canary')
        expect(rows[0].children[0].slots.map(s => s.id)).toEqual(['s2'])
    })

    it('still draws a project which has no default-qualifier row', () => {
        const rows = matrixRows([
            {project, rows: [{qualifier: 'canary', slots: [slot('s2', 'e2')]}]},
        ])
        expect(rows[0].slots).toEqual([])
        expect(rows[0].children).toHaveLength(1)
    })

    it('opens the projects with few enough qualifiers to be read at a glance', () => {
        const withQualifiers = (id, count) => ({
            project: {id, name: `p${id}`},
            rows: Array.from({length: count}, (_, index) => ({qualifier: `q${index}`, slots: []})),
        })
        const keys = defaultExpandedKeys([
            withQualifiers(1, 1),
            withQualifiers(2, 3),
            withQualifiers(3, 4),
        ])
        // One row expands nothing (there is no arrow), three open, four stay shut
        expect(keys).toEqual([projectRowKey({id: 2})])
    })
})

describe('a label in the toolbar', () => {
    it('reads as category:name, or as the name alone', () => {
        expect(labelName({category: 'team', name: 'core'})).toBe('team:core')
        expect(labelName({name: 'core'})).toBe('core')
        expect(labelName(null)).toBe('')
    })
})

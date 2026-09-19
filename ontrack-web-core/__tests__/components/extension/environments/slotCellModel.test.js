import {
    buildLabel,
    compactAge,
    deployedBuild,
    inFlightDeployment,
    inFlightLabel,
    isInFlight,
    slotDisplayName,
    slotDisplayNameWithoutProject,
    topPromotionRun,
} from "@components/extension/environments/shared/slotCellModel"

/**
 * What a slot cell is showing, decided apart from how it is drawn.
 *
 * These rules are shared by the cell, the drawer, the journey chip and the matrix, so getting one of
 * them wrong is wrong in four places at once. They are worth their own tests.
 */

/**
 * A build as the server hands it over.
 *
 * `displayName` is `String!` and the server falls back to the build's name when no display name
 * provider answers, so the fixture never leaves it unset - a fixture that did would be testing a
 * shape the server cannot produce, which is exactly how #1824 went unnoticed.
 */
const build = (name, {displayName, promotions = []} = {}) => ({
    id: name,
    name,
    displayName: displayName ?? name,
    promotionRuns: promotions.map(level => ({id: `run-${level}`, promotionLevel: {id: level, name: level}})),
})

const pipeline = (number, status, aBuild, {end} = {}) => ({
    id: `p-${number}`,
    number,
    status,
    finished: status === 'DONE' || status === 'CANCELLED',
    start: '2026-09-01T10:00:00Z',
    end: end ?? null,
    build: aBuild,
})

describe('in flight', () => {

    it('a candidate is in flight', () => {
        expect(isInFlight(pipeline(1, 'CANDIDATE', build('107')))).toBe(true)
    })

    it('a running deployment is in flight', () => {
        expect(isInFlight(pipeline(1, 'RUNNING', build('107')))).toBe(true)
    })

    it('a finished deployment is not', () => {
        expect(isInFlight(pipeline(1, 'DONE', build('107')))).toBe(false)
    })

    it('a slot whose most recent deployment is finished has nothing in flight', () => {
        // `currentPipeline` is the slot's latest deployment whatever became of it, so asking whether
        // the field is set is not asking whether something is happening.
        const slot = {currentPipeline: pipeline(3, 'DONE', build('107'))}
        expect(inFlightDeployment(slot)).toBeNull()
    })

    it('names the build and the phase', () => {
        const slot = {currentPipeline: pipeline(3, 'RUNNING', build('107'))}
        expect(inFlightLabel(slot)).toBe('→ 107 running')
    })

    it('says nothing when nothing is in flight', () => {
        expect(inFlightLabel({currentPipeline: pipeline(3, 'DONE', build('107'))})).toBeNull()
    })
})

describe('the deployed build', () => {

    it('is the last one actually deployed, not the one on its way', () => {
        // The cell shows what is running in that environment now; the in-flight build is an overlay
        // on it, not a replacement for it.
        const slot = {
            lastDeployedPipeline: pipeline(2, 'DONE', build('89')),
            currentPipeline: pipeline(3, 'RUNNING', build('107')),
        }
        expect(deployedBuild(slot).name).toBe('89')
    })

    it('is absent from a slot which was never deployed', () => {
        expect(deployedBuild({currentPipeline: null, lastDeployedPipeline: null})).toBeNull()
    })
})

describe('the top promotion', () => {

    it('is the last of the runs, which is the highest rung', () => {
        // `promotionRuns(lastPerLevel: true)` comes back in the branch's promotion order, lowest
        // first - so the top promotion is the last entry, not the first.
        expect(topPromotionRun(build('104', {promotions: ['BRONZE', 'SILVER', 'GOLD']})).promotionLevel.name)
            .toBe('GOLD')
    })

    it('is absent from an unpromoted build', () => {
        expect(topPromotionRun(build('104'))).toBeNull()
    })
})

describe('the build label', () => {

    it('is the display name', () => {
        expect(buildLabel(build('104', {displayName: '1.4.3'}))).toBe('1.4.3')
    })

    it('is the build name when the server has no display name to give', () => {
        expect(buildLabel(build('104'))).toBe('104')
    })

    it('falls back to the name when a caller forgot to ask for the display name', () => {
        expect(buildLabel({id: '104', name: '104'})).toBe('104')
    })

    it('falls back to the name rather than going blank on an empty display name', () => {
        expect(buildLabel({id: '104', name: '104', displayName: ''})).toBe('104')
    })

    it('is empty for no build at all', () => {
        expect(buildLabel(null)).toBe('')
    })
})

describe('the compact age', () => {

    const now = '2026-09-18T12:00:00Z'

    it.each([
        ['2026-09-18T11:59:40Z', 'now'],
        ['2026-09-18T11:48:00Z', '12m'],
        ['2026-09-18T09:00:00Z', '3h'],
        ['2026-08-31T12:00:00Z', '18d'],
        ['2024-09-18T12:00:00Z', '2y'],
    ])('reads %s as %s', (value, expected) => {
        expect(compactAge(value, now)).toBe(expected)
    })

    it('says nothing about nothing', () => {
        expect(compactAge(null, now)).toBe('')
    })
})

describe('how a slot is named', () => {

    const slot = (qualifier) => ({
        environment: {name: 'production'},
        project: {name: 'petclinic'},
        qualifier,
    })

    it('is "environment · project"', () => {
        expect(slotDisplayName(slot(''))).toBe('production · petclinic')
    })

    it('carries the qualifier in brackets', () => {
        expect(slotDisplayName(slot('canary'))).toBe('production · petclinic [canary]')
    })

    it('drops the project where the project is already known', () => {
        expect(slotDisplayNameWithoutProject(slot('canary'))).toBe('production [canary]')
    })
})

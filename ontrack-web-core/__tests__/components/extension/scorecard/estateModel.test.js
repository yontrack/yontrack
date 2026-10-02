import {
    durationInput,
    durationSeconds,
    estateFormValues,
    estateInput,
    estateMarkerDescription,
    estateMarkerText,
    estateReadingConfigTexts,
    estateSecurityTexts,
    latestEstateComputedAt,
} from "@components/extension/scorecard/estates/estateModel";

const estate = (props = {}) => ({
    id: 12,
    name: 'Products',
    description: 'Our products',
    labels: [
        {id: 1, category: 'type', name: 'product'},
        {id: 2, category: null, name: 'live'},
    ],
    marker: null,
    readingConfigs: [],
    projects: [],
    ...props,
})

describe('durationInput', () => {
    it('takes the largest unit which divides the duration', () => {
        expect(durationInput(86400)).toEqual({amount: 1, unit: 'days'})
        expect(durationInput(3 * 86400)).toEqual({amount: 3, unit: 'days'})
        expect(durationInput(7200)).toEqual({amount: 2, unit: 'hours'})
        expect(durationInput(90000)).toEqual({amount: 25, unit: 'hours'})
        expect(durationInput(1800)).toEqual({amount: 30, unit: 'minutes'})
    })

    it('falls back on minutes for a duration no unit divides', () => {
        expect(durationInput(90)).toEqual({amount: 1.5, unit: 'minutes'})
    })

    it('gives no amount for no duration, in hours', () => {
        expect(durationInput(null)).toEqual({amount: null, unit: 'hours'})
        expect(durationInput(undefined)).toEqual({amount: null, unit: 'hours'})
    })
})

describe('durationSeconds', () => {
    it('converts an amount in its unit to seconds', () => {
        expect(durationSeconds(1, 'days')).toBe(86400)
        expect(durationSeconds(2, 'hours')).toBe(7200)
        expect(durationSeconds(30, 'minutes')).toBe(1800)
        expect(durationSeconds(1.5, 'minutes')).toBe(90)
    })

    it('gives null for no amount', () => {
        expect(durationSeconds(null, 'hours')).toBeNull()
        expect(durationSeconds(undefined, 'days')).toBeNull()
    })

    it('round-trips through the input', () => {
        [60, 90, 1800, 3600, 7200, 86400, 90000, 604800].forEach(seconds => {
            const {amount, unit} = durationInput(seconds)
            expect(durationSeconds(amount, unit)).toBe(seconds)
        })
    })
})

describe('estateFormValues', () => {
    it('starts a new estate with the default marker and no reading configured', () => {
        const values = estateFormValues(undefined)
        expect(values.name).toBe('')
        expect(values.description).toBe('')
        expect(values.labels).toEqual([])
        expect(values.markerKind).toBe('DEFAULT')
        expect(values.readings.map(it => it.key)).toEqual([
            'delivery.leadTime',
            'delivery.frequency',
            'delivery.successRate',
            'delivery.mttr',
            'quality.testPassRate',
            'quality.testFlakiness',
            'security.maturity',
        ])
        values.readings.forEach(it => {
            expect(it.windowDays).toBeNull()
            expect(it.target).toBeNull()
        })
        expect(values.expectedKinds).toEqual([])
        expect(values.freshnessDays).toBeNull()
        expect(values.criticalTargetDays).toBeNull()
        expect(values.highTargetDays).toBeNull()
    })

    it('gives what an estate expects of the security scans', () => {
        const values = estateFormValues(estate({
            security: {expectedKinds: ['IMAGE', 'CODE'], freshnessDays: 14, criticalTargetDays: 7, highTargetDays: 30},
        }))
        expect(values.expectedKinds).toEqual(['IMAGE', 'CODE'])
        expect(values.freshnessDays).toBe(14)
        expect(values.criticalTargetDays).toBe(7)
        expect(values.highTargetDays).toBe(30)
    })

    it('gives the labels of an estate as display strings', () => {
        expect(estateFormValues(estate()).labels).toEqual(['type:product', 'live'])
    })

    it('gives a promotion marker', () => {
        const values = estateFormValues(estate({marker: {kind: 'PROMOTION', levelName: 'GOLD'}}))
        expect(values.markerKind).toBe('PROMOTION')
        expect(values.levelName).toBe('GOLD')
    })

    it('gives an environment marker with its qualifier', () => {
        const values = estateFormValues(estate({
            marker: {kind: 'ENVIRONMENT', environment: 'production', qualifier: 'eu'},
        }))
        expect(values.markerKind).toBe('ENVIRONMENT')
        expect(values.environment).toBe('production')
        expect(values.qualifier).toBe('eu')
    })

    it('gives the targets in the unit of their input, the durations in their largest unit', () => {
        const values = estateFormValues(estate({
            readingConfigs: [
                {key: 'delivery.leadTime', windowDays: 30, target: 86400},
                {key: 'delivery.frequency', windowDays: null, target: 5},
                {key: 'delivery.mttr', windowDays: null, target: 7200},
                {key: 'quality.testPassRate', windowDays: 14, target: null},
            ],
        }))
        const reading = (key) => values.readings.find(it => it.key === key)
        expect(reading('delivery.leadTime')).toEqual({key: 'delivery.leadTime', windowDays: 30, target: 1, targetUnit: 'days'})
        expect(reading('delivery.frequency')).toEqual({key: 'delivery.frequency', windowDays: null, target: 5, targetUnit: null})
        expect(reading('delivery.mttr')).toEqual({key: 'delivery.mttr', windowDays: null, target: 2, targetUnit: 'hours'})
        expect(reading('quality.testPassRate')).toEqual({key: 'quality.testPassRate', windowDays: 14, target: null, targetUnit: null})
        expect(reading('delivery.successRate')).toEqual({key: 'delivery.successRate', windowDays: null, target: null, targetUnit: null})
    })

    it('keeps the configuration of a reading out of the catalogue', () => {
        const values = estateFormValues(estate({
            readingConfigs: [{key: 'other.reading', windowDays: 7, target: 3}],
        }))
        expect(values.readings[values.readings.length - 1]).toEqual({key: 'other.reading', windowDays: 7, target: 3, targetUnit: null})
    })
})

describe('estateInput', () => {
    const values = (props = {}) => ({
        ...estateFormValues(undefined),
        name: ' Products ',
        description: 'Our products',
        labels: ['type:product'],
        ...props,
    })

    it('sends no marker for the default one', () => {
        expect(estateInput(values())).toEqual({
            name: 'Products',
            description: 'Our products',
            labels: ['type:product'],
            marker: null,
            readings: [],
            security: {expectedKinds: [], freshnessDays: null, criticalTargetDays: null, highTargetDays: null},
        })
    })

    it('sends what the estate expects of the security scans', () => {
        expect(estateInput(values({
            expectedKinds: ['DAST'],
            freshnessDays: 10,
            criticalTargetDays: 0,
            highTargetDays: undefined,
        })).security).toEqual({expectedKinds: ['DAST'], freshnessDays: 10, criticalTargetDays: 0, highTargetDays: null})
    })

    it('sends no description when it is blank', () => {
        expect(estateInput(values({description: '  '})).description).toBeNull()
    })

    it('sends a promotion marker', () => {
        expect(estateInput(values({markerKind: 'PROMOTION', levelName: 'GOLD', environment: 'production'})).marker)
            .toEqual({kind: 'PROMOTION', levelName: 'GOLD', environment: null, qualifier: null})
    })

    it('sends an environment marker, with the default qualifier when blank', () => {
        expect(estateInput(values({markerKind: 'ENVIRONMENT', environment: 'production', qualifier: ''})).marker)
            .toEqual({kind: 'ENVIRONMENT', levelName: null, environment: 'production', qualifier: ''})
        expect(estateInput(values({markerKind: 'ENVIRONMENT', environment: 'production', qualifier: 'eu'})).marker)
            .toEqual({kind: 'ENVIRONMENT', levelName: null, environment: 'production', qualifier: 'eu'})
    })

    it('sends only the readings with a window or a target, the durations in seconds', () => {
        const input = estateInput(values({
            readings: [
                {key: 'delivery.leadTime', windowDays: 30, target: 1, targetUnit: 'days'},
                {key: 'delivery.frequency', windowDays: null, target: 5, targetUnit: null},
                {key: 'delivery.successRate', windowDays: null, target: null, targetUnit: null},
                {key: 'delivery.mttr', windowDays: null, target: 90, targetUnit: 'minutes'},
                {key: 'quality.testPassRate', windowDays: 14, target: null, targetUnit: null},
                {key: 'quality.testFlakiness', windowDays: undefined, target: 0, targetUnit: null},
            ],
        }))
        expect(input.readings).toEqual([
            {key: 'delivery.leadTime', windowDays: 30, target: 86400},
            {key: 'delivery.frequency', windowDays: null, target: 5},
            {key: 'delivery.mttr', windowDays: null, target: 5400},
            {key: 'quality.testPassRate', windowDays: 14, target: null},
            {key: 'quality.testFlakiness', windowDays: null, target: 0},
        ])
    })

    it('round-trips an estate through its form', () => {
        const original = estate({
            marker: {kind: 'ENVIRONMENT', environment: 'production', qualifier: ''},
            readingConfigs: [
                {key: 'delivery.leadTime', windowDays: 30, target: 86400},
                {key: 'delivery.successRate', windowDays: null, target: 95},
                {key: 'security.maturity', windowDays: null, target: 2},
            ],
            security: {expectedKinds: ['IMAGE'], freshnessDays: 7, criticalTargetDays: 7, highTargetDays: null},
        })
        expect(estateInput(estateFormValues(original))).toEqual({
            name: 'Products',
            description: 'Our products',
            labels: ['type:product', 'live'],
            marker: {kind: 'ENVIRONMENT', levelName: null, environment: 'production', qualifier: ''},
            readings: [
                {key: 'delivery.leadTime', windowDays: 30, target: 86400},
                {key: 'delivery.successRate', windowDays: null, target: 95},
                {key: 'security.maturity', windowDays: null, target: 2},
            ],
            security: {expectedKinds: ['IMAGE'], freshnessDays: 7, criticalTargetDays: 7, highTargetDays: null},
        })
    })
})

describe('estateMarkerText', () => {
    it('names the default marker', () => {
        expect(estateMarkerText(null)).toBe('Default')
    })

    it('names a promotion marker', () => {
        expect(estateMarkerText({kind: 'PROMOTION', levelName: 'GOLD'})).toBe('Promotion: GOLD')
    })

    it('names an environment marker, with its qualifier if any', () => {
        expect(estateMarkerText({kind: 'ENVIRONMENT', environment: 'production', qualifier: ''})).toBe('Environment: production')
        expect(estateMarkerText({kind: 'ENVIRONMENT', environment: 'production', qualifier: 'eu'})).toBe('Environment: production [eu]')
    })
})

describe('estateMarkerDescription', () => {
    it('explains the default marker', () => {
        expect(estateMarkerDescription(null)).toBe('Default: the highest-ordered environment where the project has a slot, else the last promotion level of each branch')
    })

    it('names any other marker', () => {
        expect(estateMarkerDescription({kind: 'PROMOTION', levelName: 'GOLD'})).toBe('Promotion: GOLD')
        expect(estateMarkerDescription({kind: 'ENVIRONMENT', environment: 'production', qualifier: 'eu'})).toBe('Environment: production [eu]')
    })
})

describe('estateReadingConfigTexts', () => {
    it('describes the window and the target of each configured reading, in the catalogue order', () => {
        expect(estateReadingConfigTexts([
            {key: 'delivery.frequency', windowDays: null, target: 5, direction: 'HIGHER_IS_BETTER'},
            {key: 'delivery.leadTime', windowDays: 30, target: 86400, direction: 'LOWER_IS_BETTER'},
            {key: 'quality.testPassRate', windowDays: 14, target: null, direction: 'HIGHER_IS_BETTER'},
        ])).toEqual([
            {key: 'delivery.leadTime', name: 'Lead time', text: '≤ 1d, over 30 days'},
            {key: 'delivery.frequency', name: 'Frequency', text: '≥ 5 / week'},
            {key: 'quality.testPassRate', name: 'Test pass rate', text: 'over 14 days'},
        ])
    })

    it('describes nothing for no configuration', () => {
        expect(estateReadingConfigTexts(null)).toEqual([])
    })
})

describe('latestEstateComputedAt', () => {
    const project = (sets) => ({id: 1, name: 'p', scorecard: {sets}})

    it('takes the latest computation of the set of the estate among its projects', () => {
        const value = latestEstateComputedAt(estate({
            projects: [
                project([
                    {estate: null, readings: [{computedAt: '2026-09-28T05:00:00Z'}]},
                    {estate: {id: 12}, readings: [{computedAt: '2026-09-28T02:00:00Z'}]},
                ]),
                project([
                    {estate: {id: 12}, readings: [{computedAt: '2026-09-28T03:00:00Z'}]},
                    {estate: {id: 13}, readings: [{computedAt: '2026-09-28T04:00:00Z'}]},
                ]),
            ],
        }))
        expect(value).toBe('2026-09-28T03:00:00Z')
    })

    it('is null when the estate has not been computed', () => {
        expect(latestEstateComputedAt(estate())).toBeNull()
        expect(latestEstateComputedAt(estate({projects: [project([{estate: null, readings: []}])]}))).toBeNull()
    })
})

describe('estateSecurityTexts', () => {
    it('describes what an estate expects of the security scans', () => {
        expect(estateSecurityTexts({expectedKinds: ['IMAGE', 'CODE'], freshnessDays: 14, criticalTargetDays: 7, highTargetDays: 30}))
            .toEqual([
                'Image, Code scans fresher than 14 days',
                'CRITICAL fixed within 7 days, HIGH within 30 days',
            ])
    })

    it('describes the defaults: any scan, the freshness of the settings', () => {
        expect(estateSecurityTexts({expectedKinds: [], freshnessDays: null, criticalTargetDays: null, highTargetDays: null}))
            .toEqual(['Any scan, default freshness'])
        expect(estateSecurityTexts(null)).toEqual(['Any scan, default freshness'])
    })

    it('describes one remediation target only', () => {
        expect(estateSecurityTexts({expectedKinds: ['DAST'], freshnessDays: 1, criticalTargetDays: null, highTargetDays: 0}))
            .toEqual([
                'DAST scans fresher than 1 day',
                'HIGH fixed within 0 days',
            ])
    })
})

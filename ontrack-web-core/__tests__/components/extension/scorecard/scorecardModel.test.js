import {
    formatDuration,
    formatReadingValue,
    isComputedAfter,
    latestComputedAt,
    READINGS,
    readingDescription,
    readingDescriptions,
    readingDetailItems,
    readingJudgement,
    readingMarkerKinds,
    readingName,
    setTitle,
    readingUsesMarker,
    sampleCount,
    sampleCountText,
    scopeText,
    markerText,
    scorecardRows,
    sparklinePoints,
    targetText,
    unknownReasonText,
    windowDays,
} from "@components/extension/scorecard/scorecardModel";

const reading = (props = {}) => ({
    key: 'delivery.leadTime',
    day: '2026-09-28',
    computedAt: '2026-09-28T02:00:00Z',
    windowStart: '2026-06-30T02:00:00Z',
    windowEnd: '2026-09-28T02:00:00Z',
    value: 7200,
    basis: 'MEASURED',
    unknownReason: null,
    direction: 'LOWER_IS_BETTER',
    target: null,
    targetMet: null,
    details: {count: 12},
    ...props,
})

describe('formatDuration', () => {

    it('keeps the two largest units, rounded to the second', () => {
        expect(formatDuration(0)).toEqual('0s')
        expect(formatDuration(0.2)).toEqual('< 1s')
        expect(formatDuration(0.6)).toEqual('1s')
        expect(formatDuration(45.4)).toEqual('45s')
        expect(formatDuration(150)).toEqual('2m 30s')
        expect(formatDuration(3600)).toEqual('1h')
        expect(formatDuration(3720)).toEqual('1h 2m')
        expect(formatDuration(3725)).toEqual('1h 2m')
        expect(formatDuration(90000)).toEqual('1d 1h')
        expect(formatDuration(864000)).toEqual('10d')
    })

    it('has nothing to say of what is not a duration', () => {
        expect(formatDuration(null)).toEqual('-')
        expect(formatDuration(undefined)).toEqual('-')
        expect(formatDuration(-1)).toEqual('-')
        expect(formatDuration(NaN)).toEqual('-')
    })
})

describe('formatReadingValue', () => {

    it('formats a duration reading', () => {
        expect(formatReadingValue('delivery.leadTime', 7200)).toEqual('2h')
        expect(formatReadingValue('delivery.mttr', 90000)).toEqual('1d 1h')
    })

    it('formats a frequency per week', () => {
        expect(formatReadingValue('delivery.frequency', 3.5)).toEqual('3.5 / week')
        expect(formatReadingValue('delivery.frequency', 0.2333)).toEqual('0.23 / week')
        expect(formatReadingValue('delivery.frequency', 12)).toEqual('12 / week')
    })

    it('formats a percentage', () => {
        expect(formatReadingValue('delivery.successRate', 87.5)).toEqual('87.5%')
        expect(formatReadingValue('quality.testPassRate', 100)).toEqual('100%')
        expect(formatReadingValue('quality.testFlakiness', 33.3333)).toEqual('33.3%')
    })

    it('formats a rung of the security maturity with its name', () => {
        expect(formatReadingValue('security.maturity', 0)).toEqual('0 · None')
        expect(formatReadingValue('security.maturity', 1)).toEqual('1 · Reported')
        expect(formatReadingValue('security.maturity', 2)).toEqual('2 · Covered')
        expect(formatReadingValue('security.maturity', 3)).toEqual('3 · Gating')
    })

    it('formats a remediation time as a duration', () => {
        expect(formatReadingValue('security.remediationTime', 2 * 86400)).toEqual('2d')
    })

    it('formats a number of overdue findings as a whole number', () => {
        expect(formatReadingValue('security.overdue', 0)).toEqual('0')
        expect(formatReadingValue('security.overdue', 3)).toEqual('3')
    })

    it('formats the value of a reading out of the catalogue as a number', () => {
        expect(formatReadingValue('test.failing', 1.23456)).toEqual('1.23')
    })

    it('has nothing to say of no value', () => {
        expect(formatReadingValue('delivery.leadTime', null)).toEqual('-')
    })
})

describe('readingName', () => {
    it('names the readings of the catalogue, and gives the key of the others', () => {
        expect(readingName('delivery.leadTime')).toEqual('Lead time')
        expect(readingName('delivery.frequency')).toEqual('Frequency')
        expect(readingName('delivery.successRate')).toEqual('Success rate')
        expect(readingName('delivery.mttr')).toEqual('Time to restore')
        expect(readingName('quality.testPassRate')).toEqual('Test pass rate')
        expect(readingName('quality.testFlakiness')).toEqual('Test flakiness')
        expect(readingName('security.maturity')).toEqual('Security maturity')
        expect(readingName('security.remediationTime')).toEqual('Remediation time')
        expect(readingName('security.overdue')).toEqual('Overdue findings')
        expect(readingName('some.other')).toEqual('some.other')
    })
})

describe('readingDescription', () => {

    it('describes every reading of the catalogue', () => {
        READINGS.forEach(({key}) => {
            expect(readingDescription(key)).toEqual(expect.any(String))
            expect(readingDescription(key, 'ENVIRONMENT')).toEqual(expect.any(String))
        })
    })

    it('describes a delivery reading up to a promotion by default', () => {
        expect(readingDescription('delivery.leadTime')).toMatch(/first promotion/)
        expect(readingDescription('delivery.leadTime', 'PROMOTION')).toMatch(/first promotion/)
        expect(readingDescription('delivery.leadTime', null)).toMatch(/first promotion/)
    })

    it('describes a delivery reading up to an environment', () => {
        READINGS.filter(it => it.marker).forEach(({key}) => {
            expect(readingDescription(key, 'ENVIRONMENT')).not.toEqual(readingDescription(key, 'PROMOTION'))
        })
        expect(readingDescription('delivery.leadTime', 'ENVIRONMENT')).toMatch(/deployment/)
    })

    it('describes the security maturity the same way whatever the marker, without the marker', () => {
        expect(readingDescription('security.maturity', 'ENVIRONMENT')).toEqual(readingDescription('security.maturity', 'PROMOTION'))
        expect(readingDescription('security.maturity')).toMatch(/gating/)
        expect(readingUsesMarker('security.maturity')).toBe(false)
    })

    it('describes the remediation readings without the marker', () => {
        expect(readingDescription('security.remediationTime')).toMatch(/first observation/)
        expect(readingDescription('security.overdue')).toMatch(/target/)
        expect(readingUsesMarker('security.remediationTime')).toBe(false)
        expect(readingUsesMarker('security.overdue')).toBe(false)
    })

    it('describes a test reading the same way whatever the marker', () => {
        expect(readingDescription('quality.testPassRate', 'ENVIRONMENT')).toEqual(readingDescription('quality.testPassRate', 'PROMOTION'))
        expect(readingDescription('quality.testFlakiness', 'ENVIRONMENT')).toEqual(readingDescription('quality.testFlakiness', 'PROMOTION'))
    })

    it('has no description for a reading out of the catalogue', () => {
        expect(readingDescription('some.other')).toBeNull()
        expect(readingDescription('some.other', 'ENVIRONMENT')).toBeNull()
    })
})

describe('readingMarkerKinds', () => {

    it('gives the marker kinds the readings were read up to, promotion first', () => {
        expect(readingMarkerKinds([
            reading({details: {markerKind: 'ENVIRONMENT'}}),
            reading({details: {markerKind: 'PROMOTION'}}),
            reading({details: {markerKind: 'ENVIRONMENT'}}),
        ])).toEqual(['PROMOTION', 'ENVIRONMENT'])
    })

    it('ignores the readings which do not say', () => {
        expect(readingMarkerKinds([reading({details: {}}), reading({details: null}), undefined])).toEqual([])
    })
})

describe('readingDescriptions', () => {

    it('gives the one description which applies', () => {
        expect(readingDescriptions('delivery.leadTime', ['ENVIRONMENT'])).toEqual([
            {label: null, text: readingDescription('delivery.leadTime', 'ENVIRONMENT')},
        ])
        expect(readingDescriptions('delivery.leadTime', [])).toEqual([
            {label: null, text: readingDescription('delivery.leadTime', 'PROMOTION')},
        ])
    })

    it('gives both descriptions of a delivery reading read up to both kinds of marker', () => {
        expect(readingDescriptions('delivery.leadTime', ['PROMOTION', 'ENVIRONMENT'])).toEqual([
            {label: 'Up to a promotion', text: readingDescription('delivery.leadTime', 'PROMOTION')},
            {label: 'Up to an environment', text: readingDescription('delivery.leadTime', 'ENVIRONMENT')},
        ])
    })

    it('gives one description of a test reading, whatever the markers', () => {
        expect(readingDescriptions('quality.testPassRate', ['PROMOTION', 'ENVIRONMENT'])).toEqual([
            {label: null, text: readingDescription('quality.testPassRate')},
        ])
    })

    it('has nothing to say of a reading out of the catalogue', () => {
        expect(readingDescriptions('some.other', ['PROMOTION'])).toEqual([])
    })
})

describe('setTitle', () => {
    it('names the no-estate set Project, and an estate set after its estate', () => {
        expect(setTitle({name: 'Project', estate: null})).toEqual('Project')
        expect(setTitle({name: 'Demo products', estate: {name: 'Demo products'}})).toEqual('Estate: Demo products')
    })
})

describe('readingUsesMarker', () => {
    it('is true for the delivery readings only, and for the readings out of the catalogue', () => {
        expect(readingUsesMarker('delivery.leadTime')).toBe(true)
        expect(readingUsesMarker('delivery.mttr')).toBe(true)
        expect(readingUsesMarker('quality.testPassRate')).toBe(false)
        expect(readingUsesMarker('quality.testFlakiness')).toBe(false)
        expect(readingUsesMarker('some.other')).toBe(true)
    })
})

describe('unknownReasonText', () => {
    it('explains every reason', () => {
        expect(unknownReasonText('NO_MARKER')).toMatch(/marker/i)
        expect(unknownReasonText('NO_SAMPLES')).toMatch(/nothing reached the marker/i)
        expect(unknownReasonText('NO_FAILURE')).toEqual('No failure in window')
        expect(unknownReasonText('NO_TEST_STAMP')).toMatch(/test stamp/i)
        expect(unknownReasonText('NOT_LICENSED')).toMatch(/licen/i)
        expect(unknownReasonText('NO_TARGET')).toMatch(/no remediation target/i)
        expect(unknownReasonText('SOMETHING_NEW')).toEqual('SOMETHING_NEW')
    })

    it('explains a reason in the terms of its reading', () => {
        expect(unknownReasonText('NO_SAMPLES', 'security.remediationTime')).toMatch(/no CRITICAL or HIGH finding resolved/i)
        expect(unknownReasonText('NO_SAMPLES', 'delivery.leadTime')).toMatch(/nothing reached the marker/i)
        expect(unknownReasonText('NO_TARGET', 'security.overdue')).toMatch(/no remediation target/i)
    })
})

describe('readingJudgement', () => {

    it('is met or missed against a target', () => {
        expect(readingJudgement(reading({target: 10000, targetMet: true}))).toEqual('MET')
        expect(readingJudgement(reading({target: 3600, targetMet: false}))).toEqual('MISSED')
    })

    it('does not judge a reading with no target', () => {
        expect(readingJudgement(reading())).toEqual('SHOWN')
    })

    it('renders no failure as neutral, not as unknown', () => {
        expect(readingJudgement(reading({
            key: 'delivery.mttr',
            value: null,
            basis: 'UNKNOWN',
            unknownReason: 'NO_FAILURE',
            target: 3600,
        }))).toEqual('NO_FAILURE')
    })

    it('says a reading is unknown whatever its target', () => {
        expect(readingJudgement(reading({
            value: null,
            basis: 'UNKNOWN',
            unknownReason: 'NO_SAMPLES',
            target: 3600,
        }))).toEqual('UNKNOWN')
    })
})

describe('targetText', () => {
    it('gives the direction and the target in the unit of the reading', () => {
        expect(targetText(reading({target: 86400}))).toEqual('≤ 1d')
        expect(targetText(reading({key: 'delivery.frequency', direction: 'HIGHER_IS_BETTER', target: 5}))).toEqual('≥ 5 / week')
        expect(targetText(reading({key: 'delivery.successRate', direction: 'HIGHER_IS_BETTER', target: 90}))).toEqual('≥ 90%')
    })

    it('is null with no target', () => {
        expect(targetText(reading())).toBeNull()
    })
})

describe('sample counts', () => {
    it('reads the count of the details', () => {
        expect(sampleCount(reading())).toEqual(12)
        expect(sampleCount(reading({details: {count: 0}}))).toEqual(0)
        expect(sampleCount(reading({details: {testStamps: []}}))).toBeNull()
        expect(sampleCount(reading({details: null}))).toBeNull()
    })

    it('says how many samples', () => {
        expect(sampleCountText(0)).toEqual('0 samples')
        expect(sampleCountText(1)).toEqual('1 sample')
        expect(sampleCountText(12)).toEqual('12 samples')
        expect(sampleCountText(null)).toBeNull()
    })
})

describe('scorecardRows', () => {

    it('crosses the readings with the sets, in the catalogue order', () => {
        const scorecard = {
            sets: [
                {
                    name: 'Project',
                    estate: null,
                    readings: [
                        reading({key: 'delivery.frequency'}),
                        reading({key: 'delivery.leadTime'}),
                        reading({key: 'test.other'}),
                    ],
                },
                {
                    name: 'Demo products',
                    estate: {name: 'Demo products'},
                    readings: [
                        reading({key: 'delivery.mttr'}),
                        reading({key: 'delivery.leadTime', value: 60}),
                    ],
                },
            ]
        }
        const rows = scorecardRows(scorecard)
        expect(rows.map(it => it.key)).toEqual([
            'delivery.leadTime',
            'delivery.frequency',
            'delivery.mttr',
            'test.other',
        ])
        expect(rows[0].readings['Project'].value).toEqual(7200)
        expect(rows[0].readings['Demo products'].value).toEqual(60)
        expect(rows[1].readings['Demo products']).toBeUndefined()
    })

    it('has no row when nothing has been computed', () => {
        expect(scorecardRows({sets: [{name: 'Project', readings: []}]})).toEqual([])
        expect(scorecardRows(null)).toEqual([])
    })
})

describe('latestComputedAt', () => {
    it('is the time of the latest computation of any reading of any set', () => {
        expect(latestComputedAt({
            sets: [
                {readings: [reading({computedAt: '2026-09-27T02:00:00Z'})]},
                {readings: [reading({computedAt: '2026-09-28T10:15:00Z'}), reading({computedAt: '2026-09-28T09:00:00Z'})]},
            ]
        })).toEqual('2026-09-28T10:15:00Z')
    })

    it('reads the UTC date-times of the API, with no zone', () => {
        expect(latestComputedAt({
            sets: [{readings: [reading({computedAt: '2026-09-28T16:35:30.0553'}), reading({computedAt: '2026-09-28T09:00:00'})]}]
        })).toEqual('2026-09-28T16:35:30.0553')
    })

    it('is null when nothing has been computed', () => {
        expect(latestComputedAt({sets: [{readings: []}]})).toBeNull()
        expect(latestComputedAt(null)).toBeNull()
    })
})

describe('isComputedAfter', () => {
    it('compares two times of computation, nothing being earlier than anything', () => {
        expect(isComputedAfter('2026-09-28T16:35:30.0553', '2026-09-28T09:00:00')).toBe(true)
        expect(isComputedAfter('2026-09-28T09:00:00', '2026-09-28T09:00:00')).toBe(false)
        expect(isComputedAfter('2026-09-28T09:00:00', null)).toBe(true)
        expect(isComputedAfter(null, null)).toBe(false)
    })
})

describe('windowDays', () => {
    it('counts the days of the window', () => {
        expect(windowDays(reading())).toEqual(90)
        expect(windowDays(reading({windowStart: null}))).toBeNull()
    })
})

describe('markerText', () => {

    it('lists the promotion level of each branch', () => {
        expect(markerText({
            markerKind: 'PROMOTION',
            marker: {levels: {main: 'GOLD', 'release/1.0': 'SILVER'}},
        })).toEqual('Promotion: GOLD on main, SILVER on release/1.0')
    })

    it('names the environment, and the qualifier when there is one', () => {
        expect(markerText({
            markerKind: 'ENVIRONMENT',
            marker: {environment: 'production', qualifier: ''},
        })).toEqual('Environment: production')
        expect(markerText({
            markerKind: 'ENVIRONMENT',
            marker: {environment: 'production', qualifier: 'eu'},
        })).toEqual('Environment: production [eu]')
    })

    it('says when there is no marker', () => {
        expect(markerText({markerKind: 'PROMOTION', marker: null})).toEqual('No marker')
        expect(markerText({markerKind: 'ENVIRONMENT'})).toEqual('No marker')
        expect(markerText(null)).toEqual('No marker')
    })
})

describe('scopeText', () => {
    it('lists the branches read, and which case gave them', () => {
        expect(scopeText({scope: {kind: 'BRANCH_MODEL', branches: ['main', 'release/1.0']}}))
            .toEqual('main, release/1.0 (branch model)')
        expect(scopeText({scope: {kind: 'ALL_BRANCHES', branches: ['main']}}))
            .toEqual('main (all branches, no branch model)')
        expect(scopeText({scope: {kind: 'ALL_BRANCHES', branches: []}}))
            .toEqual('No branch (all branches, no branch model)')
        expect(scopeText({})).toEqual('-')
    })
})

describe('readingDetailItems', () => {

    const labels = (items) => Object.fromEntries(items.map(it => [it.label, it.text ?? it.timestamp]))

    it('gives the statistics of a duration', () => {
        const items = labels(readingDetailItems(reading({
            details: {count: 12, p90: 86400, mean: 10800, min: 60, max: 172800},
        })))
        expect(items).toEqual({
            'Samples': '12',
            '90th percentile': '1d',
            'Mean': '3h',
            'Min': '1m',
            'Max': '2d',
        })
    })

    it('gives the raw count of a frequency', () => {
        expect(labels(readingDetailItems(reading({key: 'delivery.frequency', value: 2, details: {count: 26}}))))
            .toEqual({'Samples': '26'})
    })

    it('says how the builds in flight were left out of the success rate', () => {
        const items = labels(readingDetailItems(reading({
            key: 'delivery.successRate',
            value: 75,
            details: {
                count: 8,
                promoted: 6,
                inFlight: {leadTime: 3600, since: '2026-09-28T01:00:00', excluded: 2},
            },
        })))
        expect(items).toEqual({
            'Samples': '8',
            'Promoted': '6 of 8',
            'In flight': '2 builds left out, created within 1h (the median lead time) before the end of the window',
            'In flight since': '2026-09-28T01:00:00',
        })
    })

    it('gives the deployments done and failed of an environment success rate', () => {
        expect(labels(readingDetailItems(reading({
            key: 'delivery.successRate',
            value: 80,
            details: {count: 5, done: 4, failed: 1},
        })))).toEqual({
            'Samples': '5',
            'Done': '4',
            'Failed': '1',
        })
    })

    it('gives the outages still open of a time to restore', () => {
        const items = labels(readingDetailItems(reading({
            key: 'delivery.mttr',
            value: null,
            basis: 'UNKNOWN',
            unknownReason: 'NO_SAMPLES',
            details: {count: 0, open: 1, openSince: '2026-09-20T10:00:00', inFlight: {leadTime: null, since: '2026-09-28T02:00:00', excluded: 0}},
        })))
        expect(items).toEqual({
            'Samples': '0',
            'Open outages': '1',
            'Open since': '2026-09-20T10:00:00',
            'In flight': 'No build left out',
        })
    })

    it('gives the test stamps and the builds which passed or were flaky', () => {
        expect(labels(readingDetailItems(reading({
            key: 'quality.testPassRate',
            value: 90,
            details: {count: 10, passed: 9, testStamps: ['unit', 'e2e']},
        })))).toEqual({
            'Samples': '10',
            'Passed': '9 of 10',
            'Test stamps': 'unit, e2e',
        })
        expect(labels(readingDetailItems(reading({
            key: 'quality.testFlakiness',
            value: 10,
            details: {count: 10, flaky: 1, testStamps: ['unit']},
        })))).toEqual({
            'Samples': '10',
            'Flaky': '1 of 10',
            'Test stamps': 'unit',
        })
        expect(labels(readingDetailItems(reading({
            key: 'quality.testFlakiness',
            value: null,
            basis: 'UNKNOWN',
            unknownReason: 'NO_TEST_STAMP',
            details: {testStamps: []},
        })))).toEqual({
            'Test stamps': 'None',
        })
    })

    it('gives what each rung of the security maturity rests on', () => {
        expect(labels(readingDetailItems(reading({
            key: 'security.maturity',
            value: 1,
            details: {
                count: 3,
                reported: true,
                covered: false,
                gating: true,
                expectedKinds: ['IMAGE', 'CODE'],
                freshnessDays: 14,
                freshKinds: ['IMAGE'],
                missingKinds: ['CODE'],
                failedScans: 1,
                requiredStamps: ['scan'],
                lastScan: '2026-09-27T10:00:00',
            },
        })))).toEqual({
            'Samples': '3',
            'Expected kinds': 'Image, Code',
            'Freshness': '14 days',
            'Fresh kinds': 'Image',
            'Missing kinds': 'Code',
            'Failed scans': '1',
            'Required by a promotion': 'scan',
            'Last scan': '2026-09-27T10:00:00',
        })
        expect(labels(readingDetailItems(reading({
            key: 'security.maturity',
            value: 0,
            details: {
                count: 0,
                reported: false,
                covered: false,
                gating: false,
                expectedKinds: [],
                freshnessDays: 1,
                freshKinds: [],
                missingKinds: [],
                failedScans: 0,
                requiredStamps: [],
                lastScan: null,
            },
        })))).toEqual({
            'Samples': '0',
            'Expected kinds': 'Any',
            'Freshness': '1 day',
            'Fresh kinds': 'None',
            'Failed scans': '0',
            'Required by a promotion': 'None',
        })
    })
})

describe('readingDetailItems of the remediation readings', () => {

    const labels = (items) => Object.fromEntries(items.map(it => [it.label, it.text ?? it.timestamp]))

    it('gives the statistics of the remediation time and the accepted findings', () => {
        expect(labels(readingDetailItems(reading({
            key: 'security.remediationTime',
            value: 2 * 86400,
            details: {count: 3, p90: 5 * 86400, mean: 3 * 86400, min: 86400, max: 6 * 86400, accepted: 2},
        })))).toEqual({
            'Samples': '3',
            '90th percentile': '5d',
            'Mean': '3d',
            'Min': '1d',
            'Max': '6d',
            'Accepted': '2',
        })
    })

    it('gives the overdue findings by severity, against their target', () => {
        expect(labels(readingDetailItems(reading({
            key: 'security.overdue',
            value: 2,
            details: {
                overdueCritical: 2,
                overdueHigh: null,
                openCritical: 3,
                openHigh: 1,
                criticalTargetDays: 7,
                highTargetDays: null,
                overdueSince: '2026-08-01T10:00:00',
                accepted: 1,
            },
        })))).toEqual({
            'CRITICAL': '2 overdue of 3 open, target 7 days',
            'HIGH': '1 open, no target',
            'Overdue since': '2026-08-01T10:00:00',
            'Accepted': '1',
        })
    })

    it('gives the open findings with no target at all', () => {
        expect(labels(readingDetailItems(reading({
            key: 'security.overdue',
            value: null,
            basis: 'UNKNOWN',
            unknownReason: 'NO_TARGET',
            details: {
                overdueCritical: null,
                overdueHigh: null,
                openCritical: 0,
                openHigh: 4,
                criticalTargetDays: null,
                highTargetDays: null,
                overdueSince: null,
                accepted: 0,
            },
        })))).toEqual({
            'CRITICAL': '0 open, no target',
            'HIGH': '4 open, no target',
            'Accepted': '0',
        })
    })
})

describe('sparklinePoints', () => {
    it('gives one point per day, a gap where the reading was unknown', () => {
        expect(sparklinePoints(reading({
            history: [
                {day: '2026-09-26', value: 3600},
                {day: '2026-09-27', value: null},
                {day: '2026-09-28', value: 7200},
            ]
        }))).toEqual([
            {day: '2026-09-26', value: 3600},
            {day: '2026-09-27', value: null},
            {day: '2026-09-28', value: 7200},
        ])
        expect(sparklinePoints(reading())).toEqual([])
    })
})

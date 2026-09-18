import {formatDuration} from "@components/extension/environments/slot/slotDeploymentsModel"

describe('formatDuration', () => {

    it.each([
        ['2026-09-18T10:00:00', '2026-09-18T10:00:00', '0s'],
        ['2026-09-18T10:00:00', '2026-09-18T10:00:42', '42s'],
        ['2026-09-18T10:00:00', '2026-09-18T10:01:00', '1m'],
        ['2026-09-18T10:00:00', '2026-09-18T10:01:30', '1m 30s'],
        ['2026-09-18T10:00:00', '2026-09-18T12:00:00', '2h'],
        ['2026-09-18T10:00:00', '2026-09-18T12:14:00', '2h 14m'],
        ['2026-09-18T10:00:00', '2026-09-20T10:00:00', '2d'],
        ['2026-09-18T10:00:00', '2026-09-20T13:00:00', '2d 3h'],
    ])('reads %s to %s as %s', (start, end, expected) => {
        expect(formatDuration(start, end)).toEqual(expected)
    })

    it('never shows more than two units', () => {
        // The column is narrow and the reader is scanning it for the outlier; seconds on a two-hour
        // deployment are noise.
        expect(formatDuration('2026-09-18T10:00:00', '2026-09-18T12:14:37')).toEqual('2h 14m')
    })

    it('has no duration for a deployment which has not finished', () => {
        // Deliberately not measured against "now": the row would then change on every poll, and a
        // running deployment's elapsed time is the header block's business.
        expect(formatDuration('2026-09-18T10:00:00', null)).toEqual('—')
        expect(formatDuration('2026-09-18T10:00:00', undefined)).toEqual('—')
    })

    it('has no duration for a deployment with no start', () => {
        expect(formatDuration(null, '2026-09-18T10:00:00')).toEqual('—')
    })

    it('has no duration for timestamps it cannot read', () => {
        expect(formatDuration('not-a-date', '2026-09-18T10:00:00')).toEqual('—')
    })

    it('does not report a negative duration', () => {
        // Clock skew between two writers is not a reason to print "-3s".
        expect(formatDuration('2026-09-18T10:00:03', '2026-09-18T10:00:00')).toEqual('0s')
    })
})

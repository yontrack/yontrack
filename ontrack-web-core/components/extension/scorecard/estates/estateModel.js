/**
 * What the estates admin page makes of an estate: the values of its form, the input of the
 * `createEstate` / `updateEstate` mutations, and the wording of its marker and readings. Pure
 * functions.
 *
 * The API takes the targets in the unit of their reading — seconds for the durations, per week for
 * the frequencies, 0 to 100 for the rates. The form takes a duration as an amount in minutes, hours
 * or days instead, which is converted here both ways.
 */
import {labelDisplay} from "@components/labels/LabelChip";
import {
    DURATION,
    formatReadingValue,
    READINGS,
    readingDirectionSymbol,
    readingName,
    readingRank,
    readingUnit,
} from "@components/extension/scorecard/scorecardModel";

/**
 * Kind of marker of the form for an estate with no marker: the default one.
 */
export const MARKER_DEFAULT = 'DEFAULT'
export const MARKER_PROMOTION = 'PROMOTION'
export const MARKER_ENVIRONMENT = 'ENVIRONMENT'

/**
 * Units a duration target is entered in, the largest last.
 */
export const DURATION_INPUT_UNITS = [
    {value: 'minutes', label: 'minutes', seconds: 60},
    {value: 'hours', label: 'hours', seconds: 3600},
    {value: 'days', label: 'days', seconds: 86400},
]

const DEFAULT_DURATION_UNIT = 'hours'

const isNumber = (value) => typeof value === 'number' && !isNaN(value)

const numberOrNull = (value) => isNumber(value) ? value : null

/**
 * A duration in seconds as an amount in the largest unit dividing it, in minutes when none does.
 */
export const durationInput = (seconds) => {
    if (!isNumber(seconds)) return {amount: null, unit: DEFAULT_DURATION_UNIT}
    const unit = [...DURATION_INPUT_UNITS].reverse().find(it => seconds % it.seconds === 0) ?? DURATION_INPUT_UNITS[0]
    return {amount: seconds / unit.seconds, unit: unit.value}
}

/**
 * An amount in a unit of duration, in seconds; `null` with no amount.
 */
export const durationSeconds = (amount, unit) => {
    if (!isNumber(amount)) return null
    const size = DURATION_INPUT_UNITS.find(it => it.value === unit)?.seconds ?? 3600
    return Math.round(amount * size)
}

/**
 * The configuration of one reading in the form: its window, its target in the unit of its input,
 * and the unit of a duration target (`null` for the other readings).
 */
const readingFormValues = (key, config) => {
    const windowDays = numberOrNull(config?.windowDays)
    if (readingUnit(key) === DURATION) {
        const {amount, unit} = durationInput(config?.target)
        return {key, windowDays, target: amount, targetUnit: unit}
    }
    return {key, windowDays, target: numberOrNull(config?.target), targetUnit: null}
}

/**
 * Values of the form of an estate, for its edition, or for a new estate when `estate` is not
 * defined. There is one row per reading of the catalogue, in its order, whether configured or not,
 * followed by the configured readings out of the catalogue, kept as they are.
 */
export const estateFormValues = (estate) => {
    const configs = estate?.readingConfigs ?? []
    const configOf = (key) => configs.find(it => it.key === key)
    const marker = estate?.marker
    return {
        name: estate?.name ?? '',
        description: estate?.description ?? '',
        labels: (estate?.labels ?? []).map(labelDisplay),
        markerKind: marker?.kind ?? MARKER_DEFAULT,
        levelName: marker?.levelName ?? '',
        environment: marker?.environment ?? '',
        qualifier: marker?.qualifier ?? '',
        readings: [
            ...READINGS.map(({key}) => readingFormValues(key, configOf(key))),
            ...configs
                .filter(({key}) => !READINGS.some(it => it.key === key))
                .map(config => readingFormValues(config.key, config)),
        ],
    }
}

const trimmed = (value) => (value ?? '').trim()

const markerInput = (values) => {
    switch (values.markerKind) {
        case MARKER_PROMOTION:
            return {kind: MARKER_PROMOTION, levelName: trimmed(values.levelName), environment: null, qualifier: null}
        case MARKER_ENVIRONMENT:
            return {kind: MARKER_ENVIRONMENT, levelName: null, environment: trimmed(values.environment), qualifier: trimmed(values.qualifier)}
        default:
            return null
    }
}

/**
 * Input of the `createEstate` and `updateEstate` mutations from the values of the form: the marker
 * `null` for the default one, and only the readings having a window or a target, the duration
 * targets in seconds.
 */
export const estateInput = (values) => ({
    name: trimmed(values.name),
    description: trimmed(values.description) || null,
    labels: values.labels ?? [],
    marker: markerInput(values),
    readings: (values.readings ?? [])
        .map(({key, windowDays, target, targetUnit}) => ({
            key,
            windowDays: numberOrNull(windowDays),
            target: readingUnit(key) === DURATION ? durationSeconds(target, targetUnit) : numberOrNull(target),
        }))
        .filter(({windowDays, target}) => windowDays !== null || target !== null),
})

/**
 * The marker of an estate, in words.
 */
export const estateMarkerText = (marker) => {
    if (!marker) return 'Default'
    if (marker.kind === MARKER_ENVIRONMENT) {
        return `Environment: ${marker.environment}${marker.qualifier ? ` [${marker.qualifier}]` : ''}`
    }
    return `Promotion: ${marker.levelName}`
}

/**
 * The configured readings of an estate, in the catalogue order, each with its name and its target
 * and window in words: `≤ 1d, over 30 days`.
 */
export const estateReadingConfigTexts = (readingConfigs) =>
    [...(readingConfigs ?? [])]
        .sort((a, b) => readingRank(a.key) - readingRank(b.key) || a.key.localeCompare(b.key))
        .map(({key, windowDays, target}) => {
            const parts = []
            if (isNumber(target)) {
                parts.push(`${readingDirectionSymbol(key)} ${formatReadingValue(key, target)}`.trim())
            }
            if (isNumber(windowDays)) {
                parts.push(`over ${windowDays} ${windowDays === 1 ? 'day' : 'days'}`)
            }
            return {key, name: readingName(key), text: parts.join(', ')}
        })

/**
 * Time of the latest computation of the readings of an estate among its projects, as the API gives
 * it, `null` if they have never been computed.
 */
export const latestEstateComputedAt = (estate) => {
    let latest = null
    ;(estate?.projects ?? [])
        .flatMap(project => project.scorecard?.sets ?? [])
        .filter(set => set.estate?.id === estate.id)
        .flatMap(set => set.readings)
        .forEach(({computedAt}) => {
            const time = Date.parse(computedAt)
            if (!isNaN(time) && (latest === null || time > latest.time)) {
                latest = {time, computedAt}
            }
        })
    return latest?.computedAt ?? null
}

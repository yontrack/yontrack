/**
 * The fan-out of a finding: one finding, by its external ID, over some projects — all of them, or
 * the ones of an estate — which ones are exposed, on which branches, since when.
 */
import {FINDING_SEVERITIES} from "@components/extension/findings/findingsModel";

/**
 * The external ID, or the part of it, to look for, from what the user typed: trimmed, `null` when
 * blank.
 */
export function fanOutExternalId(text) {
    const value = typeof text === 'string' ? text.trim() : ''
    return value === '' ? null : value
}

/**
 * Rank of the state of a finding in its project, the most exposed first. A finding without any
 * state comes last.
 */
const STATE_RANK = {OPEN: 0, ACCEPTED: 1, RESOLVED: 2}
const stateRank = (state) => STATE_RANK[state] ?? 3

const severityRank = (severity) => {
    const index = FINDING_SEVERITIES.indexOf(severity)
    return index < 0 ? FINDING_SEVERITIES.length : index
}

const isActive = ({state}) => state === 'EXPOSED' || state === 'ACCEPTED'

/**
 * Some ISO times, the earliest first, without the missing ones.
 */
const chronological = (times) => times.filter(it => it).sort()

/**
 * One branch, from the exposures of its stamps: exposed as soon as one of its stamps reports the
 * finding without any acceptance holding, else accepted, else resolved, since the earliest start of
 * the exposures still ongoing — of all of them when it is resolved, at the latest of its resolutions.
 */
const branchExposure = (branch, stamps) => {
    const active = stamps.filter(isActive)
    const state = active.length === 0 ? 'RESOLVED' : active.some(it => it.state === 'EXPOSED') ? 'EXPOSED' : 'ACCEPTED'
    return {
        branch,
        state,
        since: chronological((active.length > 0 ? active : stamps).map(it => it.since))[0] ?? null,
        resolvedAt: state === 'RESOLVED' ? chronological(stamps.map(it => it.resolvedAt)).at(-1) ?? null : null,
        acceptanceExpiresAt: state === 'ACCEPTED' ? (active[0].acceptanceExpiresAt ?? null) : null,
        // Whether a branch counts depends on the branch only, not on the stamp
        counts: stamps[0].counts !== false,
    }
}

const countingFirst = (branches) => [...branches.filter(it => it.counts), ...branches.filter(it => !it.counts)]

/**
 * The branches a finding has been exposed on, from its exposure per branch and stamp: a branch is
 * listed once. A branch exposed on any stamp stays exposed, and one with an acceptance holding on
 * any stamp, accepted. A branch is resolved once all its stamps are, and gives the latest of their
 * resolutions (`resolvedAt`). The resolved branches come after the exposed and accepted ones.
 *
 * Each branch says whether it `counts` toward the state of the finding in the project — matched by
 * the branch model of the project (every branch when it has none). The branches which do not count
 * are still listed, after the ones which count, within the resolved branches as well. The server
 * gives no disabled branch.
 *
 * @param {Array} exposures The `exposures` of a finding: `{branch, since, state, resolvedAt,
 * acceptanceExpiresAt, counts}` per branch and stamp
 * @returns {Array} `{branch, state, since, resolvedAt, acceptanceExpiresAt, counts}` per branch,
 * each group in the order of the exposures
 */
export function branchExposures(exposures = []) {
    const byBranch = new Map()
    ;(exposures ?? []).forEach(exposure => {
        const id = exposure.branch.id
        if (!byBranch.has(id)) {
            byBranch.set(id, {branch: exposure.branch, stamps: []})
        }
        byBranch.get(id).stamps.push(exposure)
    })
    const branches = [...byBranch.values()].map(({branch, stamps}) => branchExposure(branch, stamps))
    return [
        ...countingFirst(branches.filter(isActive)),
        ...countingFirst(branches.filter(it => !isActive(it))),
    ]
}

/**
 * State of a project for the finding: the one of its most exposed finding.
 */
const projectRank = (findings) => Math.min(...findings.map(it => stateRank(it.state)))

const groupByProject = (findings) => {
    const byProject = new Map()
    ;(findings ?? []).forEach(finding => {
        const id = finding.project.id
        if (!byProject.has(id)) {
            byProject.set(id, {project: finding.project, findings: []})
        }
        byProject.get(id).findings.push(finding)
    })
    return [...byProject.values()]
}

/**
 * The rows of the fan-out table, one per finding, grouped by project: the projects where the
 * finding is open first, then the ones where it is accepted, then the ones where it is resolved,
 * each by name. In a project, its findings — one per scanner and location — the most exposed
 * first, then the most severe, then by location. The first row of a project spans its other ones
 * (`projectRowSpan`), which span none.
 *
 * @param {Array} findings The findings of the external ID, each with its `project`, `state`,
 * `maxSeverity`, `location` and `exposures`
 * @returns {Array} `{key, project, finding, projectRowSpan, branches}` per finding
 */
export function fanOutRows(findings = []) {
    return groupByProject(findings)
        .sort((a, b) =>
            projectRank(a.findings) - projectRank(b.findings) ||
            a.project.name.localeCompare(b.project.name)
        )
        .flatMap(({project, findings: projectFindings}) =>
            [...projectFindings]
                .sort((a, b) =>
                    stateRank(a.state) - stateRank(b.state) ||
                    severityRank(a.maxSeverity) - severityRank(b.maxSeverity) ||
                    (a.location ?? '').localeCompare(b.location ?? '') ||
                    (a.scanner ?? '').localeCompare(b.scanner ?? '')
                )
                .map((finding, index) => ({
                    key: finding.id,
                    project,
                    finding,
                    projectRowSpan: index === 0 ? projectFindings.length : 0,
                    branches: branchExposures(finding.exposures),
                }))
        )
}

/**
 * How many projects report the finding, and how many of them by their most exposed finding: open
 * (`exposed`), accepted, or resolved.
 */
export function fanOutSummary(findings = []) {
    const summary = {projects: 0, exposed: 0, accepted: 0, resolved: 0}
    groupByProject(findings).forEach(({findings: projectFindings}) => {
        summary.projects++
        switch (projectRank(projectFindings)) {
            case STATE_RANK.OPEN:
                summary.exposed++
                break
            case STATE_RANK.ACCEPTED:
                summary.accepted++
                break
            case STATE_RANK.RESOLVED:
                summary.resolved++
                break
            default:
                break
        }
    })
    return summary
}

/**
 * The rows of ranked findings, in the order of the server — by number of projects where the finding
 * is open, then by severity, then by external ID — one per external ID, with the number of projects
 * reporting it.
 *
 * @param {Array} rankedFindings `Estate.rankedFindings`, or any `searchedFindings`
 * @returns {Array} The ranked findings, each with its `key` and its number of `projects`
 */
export function rankedFindingRows(rankedFindings) {
    return (rankedFindings ?? []).map(finding => ({
        ...finding,
        key: finding.externalId,
        projects: finding.openProjects + finding.acceptedProjects + finding.resolvedProjects,
    }))
}

/**
 * `Open in 3 · accepted in 1 · resolved in 0`: in how many projects a ranked finding is open,
 * accepted and resolved.
 */
export const rankedFindingProjectsText = ({openProjects, acceptedProjects, resolvedProjects}) =>
    `Open in ${openProjects} · accepted in ${acceptedProjects} · resolved in ${resolvedProjects}`

/**
 * What the list of the findings found by a search shows: the top of them when it is as long as
 * asked for, which asks for more of the external ID, else every finding found.
 *
 * @param count Number of findings listed
 * @param size Number of findings asked for
 * @param text Text searched among the external IDs
 */
export function searchedFindingsCaption(count, size, text) {
    if (count >= size) {
        return `The first ${size} findings whose external ID contains "${text}": type more of it to narrow the search`
    } else if (count === 1) {
        return `1 finding whose external ID contains "${text}"`
    } else {
        return `${count} findings whose external ID contains "${text}", the most widespread first`
    }
}

const single = (value) => Array.isArray(value) ? value[0] : value

/**
 * The state of the page of a finding across projects from the query of its URL —
 * `?finding=CVE-2024-38816`, or `?search=cve-2024` — so that a fan-out or a search can be shared by
 * its link: the external ID of the finding whose fan-out is shown, and the text searched among the
 * external IDs — each trimmed, `null` when there is none.
 *
 * A finding picked among the results of a search keeps the search, to go back to its results.
 */
export function fanOutPageState(query = {}) {
    return {
        finding: fanOutExternalId(single(query?.finding)),
        search: fanOutExternalId(single(query?.search)),
    }
}

/**
 * The query of the URL from the state of the page of a finding across projects.
 */
export function fanOutPageQuery({finding, search} = {}) {
    const query = {}
    const externalId = fanOutExternalId(finding)
    if (externalId) {
        query.finding = externalId
    }
    const text = fanOutExternalId(search)
    if (text) {
        query.search = text
    }
    return query
}

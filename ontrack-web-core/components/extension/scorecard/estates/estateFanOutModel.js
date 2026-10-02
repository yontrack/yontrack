/**
 * The findings fan-out of an estate: one finding, by its external ID, over the projects of the
 * estate — which ones are exposed, on which branches, since when.
 */
import {FINDING_SEVERITIES} from "@components/extension/findings/findingsModel";

/**
 * The external ID to look for, from what the user typed: trimmed, `null` when blank.
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

/**
 * The branches a finding is exposed on, from its exposure per branch and stamp: a branch is listed
 * once, exposed as soon as one of its stamps reports the finding without any acceptance holding,
 * else accepted, since the earliest start of the exposure of its stamps. Branches where the finding
 * is resolved are left out.
 *
 * Each branch says whether it `counts` toward the state of the finding in the project — matched by
 * the branch model of the project (every branch when it has none) and not disabled. The branches
 * which do not count are still listed, after the ones which count.
 *
 * @param {Array} exposures The `exposures` of a finding: `{branch, since, state, acceptanceExpiresAt,
 * counts}` per branch and stamp
 * @returns {Array} `{branch, state, since, acceptanceExpiresAt, counts}` per branch, the ones which
 * count first, each group in the order of the exposures
 */
export function branchExposures(exposures = []) {
    const byBranch = new Map()
    ;(exposures ?? []).forEach(({branch, since, state, acceptanceExpiresAt, counts}) => {
        if (state !== 'EXPOSED' && state !== 'ACCEPTED') return
        const current = byBranch.get(branch.id)
        if (!current) {
            byBranch.set(branch.id, {
                branch,
                state,
                since,
                acceptanceExpiresAt: state === 'ACCEPTED' ? (acceptanceExpiresAt ?? null) : null,
                // Whether a branch counts depends on the branch only, not on the stamp
                counts: counts !== false,
            })
        } else {
            if (since && (!current.since || since < current.since)) {
                current.since = since
            }
            if (state === 'EXPOSED') {
                current.state = 'EXPOSED'
                current.acceptanceExpiresAt = null
            }
        }
    })
    const branches = [...byBranch.values()]
    return [...branches.filter(it => it.counts), ...branches.filter(it => !it.counts)]
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
 * The rows of the ranked findings of an estate, in the order of the server — by number of projects
 * where the finding is open, then by severity, then by external ID — one per external ID, with the
 * number of projects reporting it.
 *
 * @param {Array} rankedFindings `Estate.rankedFindings`
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
 * `Open in 3 · accepted in 1 · resolved in 0`: in how many projects of the estate a ranked finding
 * is open, accepted and resolved.
 */
export const rankedFindingProjectsText = ({openProjects, acceptedProjects, resolvedProjects}) =>
    `Open in ${openProjects} · accepted in ${acceptedProjects} · resolved in ${resolvedProjects}`

/**
 * What the list of the ranked findings shows: the top of the ranking when it is as long as asked
 * for, else every finding open in the estate.
 *
 * @param count Number of ranked findings listed
 * @param size Number of ranked findings asked for
 */
export function rankedFindingsCaption(count, size) {
    if (count >= size) {
        return `The ${size} findings open in the most projects of this estate, the most widespread first`
    } else if (count === 1) {
        return '1 finding open in this estate'
    } else {
        return `${count} findings open in this estate, the most widespread first`
    }
}

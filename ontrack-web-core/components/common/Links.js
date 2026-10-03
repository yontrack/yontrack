import {findingsFilterToQuery} from "@components/extension/findings/findingsModel";

export function homeUri() {
    return `/`
}

export function projectUri(project) {
    return `/project/${project.id}`
}

export function projectBuildSearchUri(project) {
    return `/project/search/${project.id}`
}
export function branchUri(branch) {
    return `/branch/${branch.id}`
}

export function branchLinksUri(branch) {
    return `/branch/${branch.id}/links`
}

export function branchPromotionLevelsUri(branch) {
    return `/branch/${branch.id}/promotionLevels`
}

export function branchValidationStampsUri(branch) {
    return `/branch/${branch.id}/validationStamps`
}

export function branchAutoVersioningUri(branch) {
    return `/extension/auto-versioning/config/${branch.id}`
}

export function buildUri(build) {
    return `/build/${build.id}`
}

export function buildLinksUri(build) {
    return `/build/${build.id}/links`
}

export function buildAuditTrailUri(build) {
    return `/build/${build.id}/audit-trail`
}

/**
 * Download of the JSON export of the trail of a build, through the protected downloads of the UI,
 * which carry the token of the session.
 */
export function buildAuditTrailExportUri(build) {
    return `/api/protected/downloads/audit-trail/builds/${build.id}/export`
}

/**
 * Content of an evidence, through the protected downloads of the UI, which carry the token of the
 * session and keep the headers the backend serves it with.
 */
export function evidenceDownloadUri(evidence) {
    return `/api/protected/downloads/audit-trail/evidence/${evidence.id}`
}

/**
 * Upload of an evidence to a validation run, through the protected uploads of the UI.
 */
export function validationRunEvidenceUploadUri(run) {
    return `/api/protected/uploads/audit-trail/validation-runs/${run.id}/evidence`
}

export function scmChangeLogUri(from, to) {
    return `/extension/scm/changelog?from=${from}&to=${to}`
}

export function autoVersioningAuditEntryUri(uuid) {
    return `/extension/auto-versioning/audit/detail/${uuid}`
}

export function promotionLevelUri(promotionLevel) {
    return `/promotionLevel/${promotionLevel.id}`
}

export function promotionRunUri(promotionRun) {
    return `/promotionRun/${promotionRun.id}`
}

export function validationStampUri(validationStamp) {
    return `/validationStamp/${validationStamp.id}`
}

export function validationRunUri(validationRun) {
    return `/validationRun/${validationRun.id}`
}

/**
 * Page listing the projects carrying a label. The page itself is added by #1805;
 * the label chips and the admin page's project count already point at it.
 */
export function projectLabelUri(label) {
    return `/project-labels/${label.id}`
}

/**
 * Page of a security finding. The page itself is added by #1865; the search results of the
 * findings (#1862) already point at it.
 */
export function findingUri(finding) {
    return `/extension/findings/finding/${finding.id}`
}

/**
 * Findings page of a project, optionally filtered. The filter goes in the query of the URL, with
 * the criteria of `Project.findings(filter)`: severity, state, branch, scanner and kind.
 */
export function projectFindingsUri(project, filter = {}) {
    const query = new URLSearchParams(findingsFilterToQuery(filter)).toString()
    return `/extension/findings/project/${project.id}${query ? `?${query}` : ''}`
}

/**
 * Scorecard page of a project: each of its readings with its trend and what explains it.
 *
 * @param project Project, with its `id`
 * @param set Set to open, `project` or the name of an estate - the default set when not given
 */
export function projectScorecardUri(project, set) {
    return `/extension/scorecard/project/${project.id}${set ? `?set=${encodeURIComponent(set)}` : ''}`
}

/**
 * The scorecards of the estates: the list of the estates, each linking to its scorecard.
 */
export function scorecardsUri() {
    return '/extension/scorecard/scorecards'
}

/**
 * Scorecard of an estate: its projects × its readings.
 *
 * @param estate Estate, with its `name`
 */
export function estateScorecardUri(estate) {
    return `/extension/scorecard/estate/${encodeURIComponent(estate.name)}`
}

export function restPromotionLevelImageUri(promotionLevel) {
    return `/api/protected/images/promotionLevels/${promotionLevel.id}`
}

export function restPredefinedPromotionLevelImageUri(predefinedPromotionLevel) {
    return `/api/protected/images/predefinedPromotionLevels/${predefinedPromotionLevel.id}`
}

export function restPredefinedValidationStampImageUri(predefinedValidationStamp) {
    return `/api/protected/images/predefinedValidationStamps/${predefinedValidationStamp.id}`
}

export function restValidationStampImageUri(validationStamp) {
    return `/api/protected/images/validationStamps/${validationStamp.id}`
}

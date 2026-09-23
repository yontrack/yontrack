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

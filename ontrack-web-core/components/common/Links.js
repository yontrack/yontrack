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

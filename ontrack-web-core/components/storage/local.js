/**
 * Utilities to access the local storage
 */
import {useEffect, useState} from "react";

const selectedBuildFilter = (branchId) => `selected_build_filter_${branchId}`
const selectedValidationStampFilter = (branchId) => `selected_validation_stamp_filter_${branchId}`
const dependencyLinksMode = 'dependency_links_mode'

export function getLocallySelectedDependencyLinksMode() {
    const json = localStorage.getItem(dependencyLinksMode)
    if (json) {
        return JSON.parse(json)?.mode
    } else {
        return undefined
    }
}

export function setLocallySelectedDependencyLinksMode(mode) {
    if (mode) {
        localStorage.setItem(
            dependencyLinksMode,
            JSON.stringify({mode})
        )
    } else {
        localStorage.removeItem(dependencyLinksMode)
    }
}

export function getLocallySelectedBuildFilter(branchId) {
    const json = localStorage.getItem(selectedBuildFilter((branchId)))
    if (json) {
        return JSON.parse(json)
    } else {
        return undefined
    }
}

export function setLocallySelectedBuildFilter(branchId, buildFilterResource) {
    if (buildFilterResource) {
        localStorage.setItem(
            selectedBuildFilter((branchId)),
            JSON.stringify(buildFilterResource)
        )
    } else {
        localStorage.removeItem(
            selectedBuildFilter((branchId))
        )
    }
}

export function getLocallySelectedValidationFilter(branchId) {
    const json = localStorage.getItem(selectedValidationStampFilter((branchId)))
    if (json) {
        return JSON.parse(json)
    } else {
        return undefined
    }
}

export function setLocallySelectedValidationStampFilter(branchId, validationStampFilter) {
    if (validationStampFilter) {
        localStorage.setItem(
            selectedValidationStampFilter((branchId)),
            JSON.stringify(validationStampFilter)
        )
    } else {
        localStorage.removeItem(
            selectedValidationStampFilter((branchId))
        )
    }
}

const avAuditColumnVisibility = 'av_audit_column_visibility'

export function getAutoVersioningAuditColumnVisibility() {
    const json = localStorage.getItem(avAuditColumnVisibility)
    return json ? JSON.parse(json) : null
}

export function setAutoVersioningAuditColumnVisibility(visible) {
    localStorage.setItem(avAuditColumnVisibility, JSON.stringify(visible))
}

export const useLocalWorkflowShowDetails = () => {
    const localStorageKey = 'workflow-show-details'
    const initialValue = localStorage.getItem(localStorageKey);
    const [stateShowDetails, setStateShowDetails] = useState(initialValue === null || initialValue === 'yes')

    useEffect(() => {
        localStorage.setItem(localStorageKey, stateShowDetails ? 'yes' : 'no')
    }, [stateShowDetails])

    return {
        showDetails: stateShowDetails,
        toggleShowDetails: () => {
            setStateShowDetails(value => !value)
        },
    }
}
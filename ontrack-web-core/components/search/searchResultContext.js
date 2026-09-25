/**
 * The project a search result belongs to, when its data says: what tells apart the same commit
 * found in two projects.
 */
export const resultProjectName = (data) =>
    data?.project?.name ??
    data?.branch?.project?.name ??
    data?.build?.branch?.project?.name ??
    data?.item?.projectName ??
    null

/**
 * Where a search result is, `in <project>`, or `null` when this is not known - or when the result
 * is the project itself.
 */
export const resultContext = (result) => {
    const project = result.type?.id === 'project' ? null : resultProjectName(result.data)
    return project ? `in ${project}` : null
}

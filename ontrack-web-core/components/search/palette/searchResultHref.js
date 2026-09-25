import {branchUri, buildUri, findingUri, projectUri} from "@components/common/Links";

const withId = (entity, uri) => entity?.id !== undefined && entity?.id !== null ? uri(entity) : null

const scmItemPage = (item, page, id) =>
    item?.projectName && id ?
        `/extension/scm/${encodeURIComponent(item.projectName)}/${page}/${encodeURIComponent(id)}` :
        null

/**
 * Where each type of search result opens, from its `data`.
 *
 * The `framework/search/{type}/Result` components draw a result with links to several things (a
 * branch result links to its project too); the palette opens one page per result, the one of the
 * thing that was found.
 */
const hrefs = {
    'project': data => withId(data.project, projectUri),
    'branch': data => withId(data.branch, branchUri),
    'git-branch': data => withId(data.branch, branchUri),
    'build': data => withId(data.build, buildUri),
    'build-release': data => withId(data.build, buildUri),
    // The build declaring the link: the one the link is part of
    'build-link': data => withId(data.sourceBuild, buildUri),
    'finding': data => withId(data.finding, findingUri),
    'scm-commit': data => scmItemPage(data.item, 'commit-info', data.item?.id),
    'scm-issue': data => scmItemPage(data.item, 'issue-info', data.item?.key),
    'scm-catalog': data => withId(data.project, projectUri) ?? data.scmCatalogEntry?.repositoryPage ?? null,
}

/**
 * The page a search result opens, or `null` when there is none to open.
 */
export function searchResultHref(result) {
    const href = hrefs[result?.type?.id]
    return href && result.data ? href(result.data) ?? null : null
}

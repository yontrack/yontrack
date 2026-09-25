import {useEffect} from "react";
import {getLocalRecentlyVisited, setLocalRecentlyVisited} from "@components/storage/local";
import {branchUri, buildUri, projectUri} from "@components/common/Links";

/**
 * How many visits the palette remembers.
 */
export const RECENTLY_VISITED_MAX = 10

const sameEntity = (a, b) => a.type === b.type && a.id === b.id

/**
 * Records the visit of an entity, as the first of the recently visited ones.
 *
 * An entity already in the list moves to the top rather than being listed twice, and takes the name
 * it has now. Only the last {@link RECENTLY_VISITED_MAX} visits are kept.
 *
 * @param entry `{type, id, name, context, href}`
 */
export function recordRecentlyVisited(entry) {
    const others = getLocalRecentlyVisited().filter(it => !sameEntity(it, entry))
    setLocalRecentlyVisited([entry, ...others].slice(0, RECENTLY_VISITED_MAX))
}

export const projectVisit = (project) => project?.id ? {
    type: 'project',
    id: project.id,
    name: project.name,
    context: null,
    href: projectUri(project),
} : null

export const branchVisit = (branch) => branch?.id ? {
    type: 'branch',
    id: branch.id,
    name: branch.displayName || branch.name,
    context: branch.project?.name ?? null,
    href: branchUri(branch),
} : null

export const buildVisit = (build) => build?.id ? {
    type: 'build',
    id: build.id,
    name: build.releaseProperty?.value?.name ?? build.name,
    context: [build.branch?.project?.name, build.branch?.name].filter(Boolean).join(' / ') || null,
    href: buildUri(build),
} : null

/**
 * Records the visit of an entity page, once its entity is loaded.
 *
 * Called by the entity pages themselves - project, branch and build - with the entry built from
 * the entity they have just loaded, or `null` while it is not there yet.
 */
export function useRecordVisit(entry) {
    const key = entry ? `${entry.type}-${entry.id}-${entry.name}` : null
    useEffect(() => {
        if (entry) {
            recordRecentlyVisited(entry)
        }
        // Once per entity (and name): the entry object itself is rebuilt on every render
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [key])
}

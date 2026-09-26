import {searchResultHref} from "@components/search/palette/searchResultHref";

/**
 * Minimum length of the text before the palette searches: the server refuses anything shorter.
 */
export const MIN_SEARCH_LENGTH = 2

/**
 * Number of results the palette asks for, for each type.
 */
export const RESULTS_PER_TYPE = 3

/**
 * A count of results, followed by a plus when it is capped: the server counts the results of a type
 * up to a cap only - 1000 by default - and says when there are more.
 */
export const countLabel = (count, capped) => capped ? `${count}+` : `${count}`

/**
 * The user menu items matching a text, in the order of the menu.
 *
 * The menu the user already has is the one filtered: it reflects their rights. Every word of the text
 * must be found, whatever the case, in the name of the item or of its group - so that "config" lists
 * every configuration page.
 */
export function matchingMenuItems(menuGroups = [], text) {
    const words = text.toLowerCase().split(/\s+/).filter(Boolean)
    if (words.length === 0) return []
    return menuGroups.flatMap(group =>
        (group.items ?? [])
            .filter(item => {
                const haystack = `${group.name} ${item.name}`.toLowerCase()
                return words.every(word => haystack.includes(word))
            })
            .map(item => ({group, item}))
    )
}

/**
 * The search results grouped by type, the types in the order of their best result, each with the
 * number of results the server counts for it, and whether this count is capped.
 */
export function groupSearchResults(search) {
    const groups = []
    const byType = {}
    search?.items?.forEach(result => {
        const typeId = result.type?.id
        if (!typeId) return
        let group = byType[typeId]
        if (!group) {
            const facet = search.facets?.find(it => it.type?.id === typeId)
            group = {type: result.type, count: facet?.count, capped: facet?.capped ?? false, results: []}
            byType[typeId] = group
            groups.push(group)
        }
        group.results.push(result)
    })
    return groups.map(group => ({...group, count: group.count ?? group.results.length}))
}

/**
 * The page listing all the results of a text.
 */
export const allResultsHref = (text) => `/search?q=${encodeURIComponent(text)}`

/**
 * What the palette lists, as sections of options.
 *
 * - Nothing typed: the recently visited entities.
 * - Some text: the user menu items matching it, then - from {@link MIN_SEARCH_LENGTH} characters -
 *   the search results grouped by type, and an entry opening all the results.
 *
 * The menu items come first because they are there at once: the search results arrive later, and
 * must not move the active option from under the reader.
 *
 * Each option is `{key, kind, href, ...}`, `kind` being `recent`, `menu`, `result` or `all`.
 */
export function paletteSections({text, recent = [], menuGroups = [], search = null}) {
    const trimmed = text.trim()
    if (!trimmed) {
        return recent.length > 0 ? [{
            key: 'recent',
            kind: 'recent',
            title: 'Recently visited',
            options: recent.map(entry => ({
                key: `recent-${entry.type}-${entry.id}`,
                kind: 'recent',
                href: entry.href,
                entry,
            })),
        }] : []
    }

    const sections = []

    const menuItems = matchingMenuItems(menuGroups, trimmed)
    if (menuItems.length > 0) {
        sections.push({
            key: 'menu',
            kind: 'menu',
            title: 'Menu',
            options: menuItems.map(({group, item}) => ({
                key: `menu-${item.extension}-${item.id}`,
                kind: 'menu',
                href: `/${item.extension}/${item.id}`,
                group,
                item,
            })),
        })
    }

    if (trimmed.length >= MIN_SEARCH_LENGTH) {
        groupSearchResults(search).forEach(group => {
            sections.push({
                key: `type-${group.type.id}`,
                kind: 'type',
                title: `${group.type.name} (${countLabel(group.count, group.capped)})`,
                type: group.type,
                count: group.count,
                capped: group.capped,
                options: group.results.map((result, index) => ({
                    key: `result-${group.type.id}-${index}`,
                    kind: 'result',
                    href: searchResultHref(result) ?? `${allResultsHref(trimmed)}&type=${encodeURIComponent(group.type.id)}`,
                    result,
                })),
            })
        })
        sections.push({
            key: 'all',
            kind: 'all',
            title: 'All results',
            options: [{
                key: 'all',
                kind: 'all',
                href: allResultsHref(trimmed),
                text: trimmed,
            }],
        })
    }

    return sections
}

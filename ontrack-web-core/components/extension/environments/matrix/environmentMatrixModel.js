/**
 * The reading half of the matrix, kept apart from the drawing half.
 *
 * What a column is, what a row is, which rows open by default and how the toolbar's state becomes a
 * URL and a GraphQL filter - all of it is arithmetic over what the server answered, so it lives here
 * and can be tested without rendering a table.
 */

/** The scope toggle: the user's starred projects, or every project. */
export const SCOPE_FAVOURITES = 'favourites'
export const SCOPE_ALL = 'all'

/**
 * The matrix as it opens when nothing says otherwise.
 *
 * Favourites rather than All, because the screen is designed for about a hundred projects and the
 * first question is "mine". A user who has never starred anything is moved to All by the screen,
 * which needs the server's `hasFavourites` to tell that apart from "my favourites have no slot".
 */
export const defaultMatrixFilter = () => ({
    project: '',
    scope: SCOPE_FAVOURITES,
    label: null,
    tags: [],
    activity: false,
})

/** How many projects one page of the matrix holds. */
export const MATRIX_PAGE_SIZE = 20

/**
 * The filter, read from the URL, then from what was last used, then from the defaults.
 *
 * The URL wins because a link is an instruction: somebody who pasted
 * `?scope=all&tags=production` means to see that, whatever their own last choice was. The stored
 * preference is only the starting point of a visit with no instruction in it.
 *
 * @param {Object} query The Next router query
 * @param {?Object} stored What `@components/storage/local` remembered, if anything
 */
export const filterFromQuery = (query, stored) => {
    const base = {...defaultMatrixFilter(), ...(stored ?? {})}
    if (!query) return base
    const single = (value) => Array.isArray(value) ? value[0] : value
    const list = (value) => {
        if (value === undefined || value === null || value === '') return undefined
        return (Array.isArray(value) ? value : String(value).split(',')).filter(it => it !== '')
    }

    const project = single(query.project)
    const scope = single(query.scope)
    const label = single(query.label)
    const tags = list(query.tags)
    const activity = single(query.activity)

    return {
        project: project !== undefined ? project : base.project,
        scope: scope === SCOPE_ALL || scope === SCOPE_FAVOURITES ? scope : base.scope,
        label: label !== undefined && label !== '' ? Number(label) : (label === '' ? null : base.label),
        tags: tags !== undefined ? tags : base.tags,
        activity: activity !== undefined ? activity === 'true' : base.activity,
    }
}

/**
 * The query parameters a filter becomes - only what is not the default.
 *
 * A URL carrying every field even when nothing is filtered is noise in the address bar and, worse,
 * makes two identical screens look like different addresses.
 */
export const filterToQuery = (filter) => {
    const query = {}
    if (filter.project) query.project = filter.project
    if (filter.scope && filter.scope !== SCOPE_FAVOURITES) query.scope = filter.scope
    if (filter.label !== null && filter.label !== undefined) query.label = String(filter.label)
    if (filter.tags && filter.tags.length > 0) query.tags = filter.tags.join(',')
    if (filter.activity) query.activity = 'true'
    return query
}

/**
 * The GraphQL `EnvironmentMatrixFilter` a toolbar state becomes.
 *
 * The screen says "Favourites / All" and the server takes a boolean: the toggle is a scope on one
 * side and a filter on the other, and this is the one place the two vocabularies meet.
 */
export const filterToInput = (filter) => ({
    project: filter.project ? filter.project : null,
    // Pins, set by a widget's configuration rather than by the toolbar: exact names, where the
    // toolbar's `project` is a fragment somebody typed.
    projects: filter.projects ?? [],
    environments: filter.environments ?? [],
    favourites: filter.scope === SCOPE_FAVOURITES,
    label: filter.label ?? null,
    tags: filter.tags ?? [],
    activity: !!filter.activity,
})

/**
 * The columns, grouped under their first tag.
 *
 * Environments arrive in order, so a group is a *run* of consecutive environments sharing their
 * first tag rather than every environment carrying it: ordering the columns by anything other than
 * `order` would break the left-to-right reading of a delivery pipeline, which is the whole point of
 * the column order. An environment with no tag forms its own untagged group and is drawn without a
 * heading.
 *
 * @return {Array<{tag: ?string, environments: Array}>}
 */
export const groupEnvironments = (environments) => {
    const groups = []
    ;(environments ?? []).forEach(environment => {
        const tag = environment.tags && environment.tags.length > 0 ? environment.tags[0] : null
        const last = groups[groups.length - 1]
        if (last && last.tag === tag) {
            last.environments.push(environment)
        } else {
            groups.push({tag, environments: [environment]})
        }
    })
    return groups
}

/**
 * The rows of the table: one per project, with the qualifier rows nested under it.
 *
 * The empty qualifier is not a child row, it *is* the project row - the spec's "the empty qualifier
 * is the project row" - so a project with only the default qualifier is one flat row and nothing
 * expands. A project with more gets antd tree children, which is what gives the expand arrow.
 */
export const matrixRows = (projects) =>
    (projects ?? []).map(entry => {
        const rows = entry.rows ?? []
        const defaultRow = rows.find(row => row.qualifier === '')
        const qualified = rows.filter(row => row.qualifier !== '')
        const base = {
            key: projectRowKey(entry.project),
            project: entry.project,
            qualifier: '',
            slots: defaultRow?.slots ?? [],
        }
        if (qualified.length === 0) return base
        return {
            ...base,
            children: qualified.map(row => ({
                key: `${projectRowKey(entry.project)}-${row.qualifier}`,
                project: entry.project,
                qualifier: row.qualifier,
                slots: row.slots ?? [],
            })),
        }
    })

/** The key of a project's own row, which is also what the expanded set holds. */
export const projectRowKey = (project) => `project-${project.id}`

/**
 * Which projects open expanded: the ones with few enough qualifiers to be read at a glance.
 *
 * Three is the spec's number, and the reason is the screen and not the data: a project with two
 * qualifiers adds two lines, a project with twelve pushes everything below it off the page, and the
 * matrix exists to be read in one glance.
 */
export const defaultExpandedKeys = (projects, limit = 3) =>
    (projects ?? [])
        .filter(entry => (entry.rows ?? []).length > 1 && (entry.rows ?? []).length <= limit)
        .map(entry => projectRowKey(entry.project))

/**
 * The slot of one row in one environment, or `null` when the project has no slot there.
 *
 * Null is not the same as an empty slot, and the table draws nothing rather than a dash: see
 * `SlotCell`.
 */
export const slotAt = (row, environmentId) =>
    (row?.slots ?? []).find(slot => slot.environment?.id === environmentId) ?? null

/**
 * Is anything filtered at all? What the "no result" screen asks before offering to widen.
 *
 * The scope is part of it: sitting on Favourites *is* a filter, and it is the most common reason an
 * instance full of environments shows an empty matrix.
 */
export const isFiltered = (filter) =>
    !!filter.project ||
    filter.scope === SCOPE_FAVOURITES ||
    filter.label !== null && filter.label !== undefined ||
    (filter.tags ?? []).length > 0 ||
    !!filter.activity

/** How a label reads in the toolbar: "category:name", or just the name when it has no category. */
export const labelName = (label) =>
    label?.category ? `${label.category}:${label.name}` : (label?.name ?? '')

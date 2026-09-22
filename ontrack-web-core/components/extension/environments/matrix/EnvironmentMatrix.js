import {useEffect, useState} from "react"
import {Button, Empty, Space, Typography} from "antd"
import {useQuery} from "@components/services/GraphQL"
import {useEventForRefresh} from "@components/common/EventsContext"
import {useFreshness, FRESHNESS_INTERVAL_MS} from "@components/extension/environments/shared/Freshness"
import SlotDrawer from "@components/extension/environments/shared/SlotDrawer"
import {useSlotDrawer} from "@components/extension/environments/shared/useSlotDrawer"
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog"
import EnvironmentMatrixTable from "@components/extension/environments/matrix/EnvironmentMatrixTable"
import EnvironmentMatrixToolbar from "@components/extension/environments/matrix/EnvironmentMatrixToolbar"
import {
    defaultExpandedKeys,
    filterToInput,
    isFiltered,
    MATRIX_PAGE_SIZE,
    SCOPE_ALL,
    SCOPE_FAVOURITES,
} from "@components/extension/environments/matrix/environmentMatrixModel"
import {
    gqlEnvironmentMatrix,
    gqlEnvironmentsCount,
} from "@components/extension/environments/matrix/environmentMatrixGraphQL"

/**
 * Where a reader with no environment at all is sent to find out what they are.
 *
 * The user documentation lives in mkdocs under `ontrack-docs/docs/content/`, and the rendered site
 * is not served from the instance, so the link points at the source page on GitHub - which is
 * reachable from any instance and cannot go stale against a version this UI does not know.
 */
export const ENVIRONMENTS_DOC_URI =
    "https://github.com/yontrack/yontrack/blob/main/ontrack-docs/docs/content/integrations/environments/environments.md"

/**
 * The Environments matrix, with its own data.
 *
 * One component serves the home page and both dashboard widgets, because the three differ only in
 * what they are allowed to change: the page has a toolbar, a pager and the whole range of empty
 * states; a widget has a fixed filter, no toolbar and, for the environment widget, a single column.
 * What they share - the query, the polling, the drawer, the deploy dialog - is here, once.
 *
 * @param {Object} filter The matrix filter, in the screen's own vocabulary (see
 *   `environmentMatrixModel`)
 * @param {?function} onFilter Called with the changed fields. Absent means a pinned filter and no
 *   toolbar.
 * @param {number} pageSize How many projects to ask for
 * @param {boolean} paged Whether to draw a pager. A widget shows its first N rows and stops.
 * @param {boolean} emptyStates Whether to explain an empty matrix at length. The page does; a widget
 *   has room for one line.
 * @param {number} pollingIntervalMs How often to ask again. 0 disables polling.
 * @param {?Object} freshness A `useFreshness` state to share rather than one of its own. The project
 *   environments screen (#1795) draws the matrix as one of two views and owns a single "Updated N s
 *   ago" for both, so the age a reader sees does not jump when they switch view.
 */
export default function EnvironmentMatrix({
                                              filter,
                                              onFilter,
                                              pageSize = MATRIX_PAGE_SIZE,
                                              paged = true,
                                              emptyStates = true,
                                              pollingIntervalMs = FRESHNESS_INTERVAL_MS,
                                              freshness: sharedFreshness,
                                          }) {

    // Hooks cannot be skipped, so the matrix's own is created either way and simply not used when
    // the screen above has one to share. Its timer is the only cost, and it is one `setInterval`.
    const ownFreshness = useFreshness({intervalMs: sharedFreshness ? 0 : pollingIntervalMs})
    const freshness = sharedFreshness ?? ownFreshness

    /*
     * Polling every thirty seconds is right for a deployment somebody else started; it is far too
     * slow for a slot this very reader has just created from the Setup menu. Creating an
     * environment or a slot changes which columns and which rows exist at all, so those three
     * events refresh the matrix at once rather than leaving it half a minute behind its own header.
     */
    const environmentCreated = useEventForRefresh("environment.created")
    const environmentDeleted = useEventForRefresh("environment.deleted")
    const slotCreated = useEventForRefresh("slot.created")

    const [offset, setOffset] = useState(0)
    const input = filterToInput(filter)
    // The filter is an object rebuilt on every render, so its identity cannot drive the effect: its
    // *content* is what decides whether the matrix has to be asked for again.
    const inputKey = JSON.stringify(input)

    const {data: matrix, loading, finished} = useQuery(gqlEnvironmentMatrix, {
        variables: {filter: input, offset, size: pageSize},
        deps: [
            inputKey, offset, pageSize, freshness.refreshCount,
            environmentCreated, environmentDeleted, slotCreated,
        ],
        initialData: null,
        dataFn: data => data.environmentMatrix,
    })

    const {data: environmentsCount} = useQuery(gqlEnvironmentsCount, {
        deps: [environmentCreated, environmentDeleted],
        initialData: null,
        dataFn: data => data.environmentsCount,
    })

    /*
     * "Favourites by default, All when the user has none." The screen cannot tell "you have no
     * favourite" from "your favourites have no slot" on its own - the two deserve opposite answers,
     * one a silent switch and the other a hint - so the server says which it is and this moves the
     * scope.
     *
     * It *replaces* the history entry rather than adding one: a correction the screen made on its
     * own is not a step the reader took, and a Back onto it would either appear to do nothing or,
     * where the preference cannot be stored, undo itself and trap the reader on the page.
     */
    useEffect(() => {
        if (onFilter && matrix && filter.scope === SCOPE_FAVOURITES && matrix.hasFavourites === false) {
            onFilter({scope: SCOPE_ALL}, {replace: true})
        }
    }, [matrix?.hasFavourites, filter.scope])

    // Which projects are open: the server's answer decides until the reader says otherwise, and
    // then the reader's choice stands. Derived in the render body rather than kept in step by an
    // effect, so the first render already has the right rows open.
    const [expandedOverride, setExpandedOverride] = useState(null)
    const expandedRowKeys = expandedOverride ?? defaultExpandedKeys(matrix?.projects)

    const slotDrawer = useSlotDrawer()
    const deployDialog = useDeployDialog()

    const empty = finished && matrix && matrix.projects.length === 0

    /**
     * Which of the three empty screens applies, or null when the matrix has rows.
     *
     * A widget has room for none of them but the last, so `emptyStates` turns the two explanatory
     * ones off rather than squeezing a paragraph into a dashboard tile.
     */
    const emptyState = !empty ? null :
        (emptyStates && environmentsCount === 0) ? 'no-environment' :
            // Whatever the reason the Favourites scope is empty, the way out of it is the same
            // button. Offering it only to somebody who *has* a favourite leaves the one who
            // deliberately picked Favourites with none looking at "nothing matches" and no way
            // back - and they reached that screen by clicking, so they can reach it again.
            (emptyStates && filter.scope === SCOPE_FAVOURITES) ? 'favourites' :
                'filter'

    return (
        <>
            <Space orientation="vertical" className="ot-line" size="middle">
                {
                    onFilter &&
                    <EnvironmentMatrixToolbar
                        filter={filter}
                        onFilter={changes => {
                            // A new filter is a new list: staying on page 4 of a list which may now
                            // be one page long shows an empty matrix and no reason for it.
                            setOffset(0)
                            onFilter(changes)
                        }}
                        freshness={freshness}
                    />
                }
                {
                    /*
                     * Three ways to be empty, and they deserve opposite screens. "This instance has
                     * no environment" is an explanation of a feature somebody has not set up;
                     * "your favourites have no slot" is one click away from being fixed; "nothing
                     * matches" is a filter to widen. A single "no data" would answer none of them.
                     */
                    emptyState === 'no-environment' &&
                    <Empty
                        data-testid="matrix-empty-no-environment"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                            <Space orientation="vertical">
                                <Typography.Text>
                                    No environment has been created yet. Environments and their
                                    deployment slots describe where your builds run, and are usually
                                    declared as code rather than created by hand - the Setup command
                                    above does it by hand when you need to.
                                </Typography.Text>
                                <Typography.Link
                                    href={ENVIRONMENTS_DOC_URI}
                                    target="_blank"
                                    data-testid="matrix-empty-doc-link"
                                >
                                    Read the documentation
                                </Typography.Link>
                            </Space>
                        }
                    />
                }
                {
                    emptyState === 'favourites' &&
                    <Empty
                        data-testid="matrix-empty-favourites"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                            <Space orientation="vertical">
                                <Typography.Text>
                                    None of your favourite projects has a deployment slot.
                                </Typography.Text>
                                <Button
                                    type="primary"
                                    data-testid="matrix-show-all"
                                    onClick={() => onFilter && onFilter({scope: SCOPE_ALL})}
                                >
                                    Show all
                                </Button>
                            </Space>
                        }
                    />
                }
                {
                    emptyState === 'filter' &&
                    <Empty
                        data-testid="matrix-empty-filter"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                            isFiltered(filter) ?
                                "No project matches the current filter." :
                                "No project has a deployment slot yet."
                        }
                    />
                }
                {
                    !empty &&
                    <EnvironmentMatrixTable
                        matrix={matrix}
                        loading={loading || !finished}
                        onSlotClick={slotDrawer.openSlot}
                        expandedRowKeys={expandedRowKeys}
                        onExpandedRowsChange={keys => setExpandedOverride(Array.from(keys))}
                        pagination={
                            paged && matrix ?
                                {
                                    current: Math.floor(offset / pageSize) + 1,
                                    pageSize,
                                    total: matrix.totalProjects,
                                    showSizeChanger: false,
                                    onChange: (page) => setOffset((page - 1) * pageSize),
                                } :
                                false
                        }
                    />
                }
            </Space>
            <SlotDrawer
                slotId={slotDrawer.slotId}
                open={slotDrawer.open}
                onClose={slotDrawer.close}
                onDeploy={(slot, build) => deployDialog.start({slot, build})}
            />
            <DeployDialog dialog={deployDialog}/>
        </>
    )
}

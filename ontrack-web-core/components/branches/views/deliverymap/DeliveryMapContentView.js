import {useContext, useEffect, useMemo, useState} from "react";
import {Alert, Space, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {AutoRefreshButton, AutoRefreshContext, AutoRefreshContextProvider} from "@components/common/AutoRefresh";
import CloseableAlert from "@components/common/CloseableAlert";
import LoadingContainer from "@components/common/LoadingContainer";
import {ValidationStampFilterContext} from "@components/branches/filters/validationStamps/ValidationStampFilterContext";
import {gqlDeliveryMap} from "@components/branches/views/deliverymap/deliveryMapQueries";
import {
    getLocalDeliveryMapVisibility,
    setLocalDeliveryMapVisibility,
} from "@components/storage/local";
import {
    applyValidationStampFilter,
    hasNoDependencies,
    isMapEmpty,
} from "@components/branches/views/deliverymap/deliveryMapModel";
import {applyVisibility} from "@components/branches/views/deliverymap/visibilityKinds";
import DeliveryMapHeader from "@components/branches/views/deliverymap/DeliveryMapHeader";
import DeliveryMapVisibility from "@components/branches/views/deliverymap/DeliveryMapVisibility";
import DeliveryMapGraph from "@components/branches/views/deliverymap/DeliveryMapGraph";
import DeliveryMapEmpty from "@components/branches/views/deliverymap/DeliveryMapEmpty";
import DeliveryMapNoDependencies from "@components/branches/views/deliverymap/DeliveryMapNoDependencies";
import DeliveryMapSlotDrawer from "@components/extension/environments/deliverymap/DeliveryMapSlotDrawer";

/**
 * The delivery map branch content view: what a build on this branch has to pass through on its way
 * to an environment.
 *
 * A peer of the builds and pipeline views on the single axis of choice established by ADR 0001, and
 * like the pipeline view it does NOT own the validation stamp filter, which lives above the view
 * switch so that a user's filter follows them from one view to the next. It reads it and narrows the
 * map by it.
 *
 * What it does own is the branch head in its header - the build every checkpoint's lag is counted
 * against - what is drawn on the map at all, and its own auto refresh. All three are ways of reading
 * this one graph rather than statements about the branch.
 *
 * The auto refresh context is provided HERE and consumed by the view below, which is why the two are
 * separate components: a component cannot read a context it provides itself.
 *
 * @param branch Branch being displayed
 */
export default function DeliveryMapContentView({branch}) {
    return (
        <AutoRefreshContextProvider>
            {/* The slot drawer a slot checkpoint opens, and the deploy dialog behind it. Core to
                extension, like the registry's entry for the slot kind and for the same reason: the
                kinds on this map are an open set, and the extension contributing one owns both the
                checkpoint and what clicking it does. */}
            <DeliveryMapSlotDrawer>
                <DeliveryMapContent branch={branch}/>
            </DeliveryMapSlotDrawer>
        </AutoRefreshContextProvider>
    )
}

/**
 * The view itself, inside the auto refresh context.
 *
 * @param branch Branch being displayed
 */
function DeliveryMapContent({branch}) {

    const vsfContext = useContext(ValidationStampFilterContext)

    // Starting with everything shown, which is also the stored default, so that the map never lays
    // itself out twice on arrival. The preference is read in an effect rather than at first render
    // because the local storage is not there to be read while the page is rendered on the server.
    const [visibility, setVisibility] = useState({})
    useEffect(() => {
        setVisibility(getLocalDeliveryMapVisibility())
    }, [])

    const onToggleVisibility = (id, shown) => {
        const updated = {...visibility, [id]: shown}
        setLocalDeliveryMapVisibility(updated)
        setVisibility(updated)
    }

    // Refetched on every tick of the auto refresh, as `BranchLinksGraph` does it: the count is an
    // effect dependency rather than an `onRefresh` callback, so the query owns its own reloading.
    const {autoRefreshCount} = useContext(AutoRefreshContext)

    const {data, error, finished} = useQuery(
        gqlDeliveryMap,
        {
            variables: {branchId: Number(branch.id)},
            deps: [branch, autoRefreshCount],
            initialData: null,
            dataFn: data => data.branch?.deliveryMap,
        }
    )

    // Derived from what is already in hand, not stored: a `useState` filled by an effect would leave
    // the graph laid out against an empty map for one render, and React Flow syncs its own layout
    // against whatever it is given at that moment.
    //
    // Memoised because the graph re-runs the elk layout whenever this changes IDENTITY, and
    // narrowing by a filter builds a new object every time it is called. Without the memo, any
    // re-render of this component while a filter is selected - starting inline edition of that
    // filter, say - would reshuffle the whole map and throw away any node the user had dragged.
    const map = useMemo(
        () => applyVisibility(
            applyValidationStampFilter(data, vsfContext.selectedFilter),
            visibility,
        ),
        [data, vsfContext.selectedFilter, visibility],
    )

    return (
        <Space direction="vertical" size={16} className="ot-line">
            {/* The loud half of the experimental marking, as the pipeline view does it: dismissible,
                and carrying the invitation to give feedback. `info` rather than `warning` - the view
                is new, not risky, and a yellow band argues against adopting what we are asking
                people to adopt. */}
            <CloseableAlert
                id="feature-branch-delivery-map-view"
                type="info"
                message={
                    <Typography.Text data-testid="delivery-map-experimental-alert">
                        The Delivery map view is experimental and still being refined. Your feedback
                        is welcome — tell us what works and what does not in{' '}
                        <a href="https://github.com/yontrack/yontrack/discussions"
                           target="_blank" rel="noreferrer">
                            GitHub Discussions
                        </a>.
                    </Typography.Text>
                }
            />
            {/* Only until the FIRST answer, never on a refresh. `useQuery` raises `loading` again
                on every refetch, and reading it here would replace the map with a skeleton once a
                minute - which unmounts the graph, and takes the layout and every node the user had
                dragged with it. `finished` stays true once the first fetch has answered, which is
                exactly the "have we ever had a map" this needs. */}
            <LoadingContainer loading={!finished}>
                {/* The same 16px rhythm as the outer stack, and it cannot BE the outer stack:
                    everything below is one child of the `LoadingContainer`, which renders no wrapper
                    of its own between them. Without it the toolbar sits flush against whatever
                    follows - the "nothing joins these checkpoints" notice most visibly. */}
                <Space direction="vertical" size={16} className="ot-line">
                    {/* Above the empty state as well as above the map: the header is this view's
                        toolbar, and a control which comes and goes with the data is a control the
                        reader cannot count on. A branch with nothing on its map still has a latest
                        build, and still refreshes. */}
                    <DeliveryMapHeader
                        head={data?.head}
                        extra={
                            <>
                                <DeliveryMapVisibility
                                    visibility={visibility}
                                    onToggle={onToggleVisibility}
                                />
                                <AutoRefreshButton size="small"/>
                            </>
                        }
                    />
                    {
                        // A failed fetch is said in words. It is NOT left to the empty state: a
                        // refresh which fails - a backend restart, one bad response out of sixty -
                        // nulls the data, and "this branch has nothing on its map" is then a claim
                        // about the branch which happens to be false. The header stays above it, so
                        // the reader can still turn the refresh off.
                        error ?
                            <Alert
                                type="error"
                                showIcon
                                data-testid="delivery-map-error"
                                message="The delivery map could not be loaded"
                                description={error}
                            /> :
                        isMapEmpty(map) ?
                            <DeliveryMapEmpty/> :
                            <>
                                {/* Read on the map the SERVER sent, never on the narrowed one: this
                                    notice tells the reader to go and configure auto promotion, and
                                    hiding the stamps - or filtering them out - must not make the map
                                    ask for configuration which is already there. */}
                                {hasNoDependencies(data) && <DeliveryMapNoDependencies/>}
                                <DeliveryMapGraph map={map}/>
                            </>
                    }
                </Space>
            </LoadingContainer>
        </Space>
    )
}

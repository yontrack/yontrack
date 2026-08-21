import {useContext, useEffect, useRef, useState} from "react";
import GridTable from "@components/grid/GridTable";
import {StoredGridLayoutContext} from "@components/grid/StoredGridLayoutContext";
import GridTableContextProvider from "@components/grid/GridTableContext";
import {getLocalGridLayout, setLocalGridLayout} from "@components/storage/local";

export default function StoredGridLayout({id, defaultLayout, items, rowHeight = 200, isDraggable}) {

    const [layout, setLayout] = useState(defaultLayout)
    const [loaded, setLoaded] = useState(false)

    // A layout restored from the local storage must not be overridden by the default
    // layout, which some pages compute only after their content has been loaded
    const restored = useRef(false)

    useEffect(() => {
        const storedLayout = getLocalGridLayout(id)
        if (storedLayout) {
            restored.current = true
            setLayout(storedLayout)
        }
        setLoaded(true)
    }, []);

    // The default layout is followed as long as no layout has been restored. Its
    // content is used as the dependency, not the array itself: pages recreate it
    // at each rendering, and following its identity would discard the layout the
    // user is working with.
    const defaultLayoutContent = JSON.stringify(defaultLayout)
    useEffect(() => {
        if (!restored.current) {
            setLayout(defaultLayout)
        }
    }, [defaultLayoutContent]);

    // Resetting the layout goes back to the default one, and gives the control
    // back to it
    const {resetLayoutCount} = useContext(StoredGridLayoutContext)
    useEffect(() => {
        if (resetLayoutCount) {
            restored.current = false
            setLayout(defaultLayout)
        }
    }, [resetLayoutCount]);

    const onLayoutChange = (newLayout) => {
        setLocalGridLayout(id, newLayout)
    }

    return (
        <>
            {
                loaded &&
                <GridTableContextProvider isDraggable={isDraggable}>
                    <GridTable
                        rowHeight={rowHeight}
                        layout={layout}
                        onLayoutChange={onLayoutChange}
                        items={items}
                    />
                </GridTableContextProvider>
            }
        </>
    )
}
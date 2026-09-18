import BuildContentValidations from "@components/builds/BuildContentValidations";
import BuildContentLinksUsing from "@components/builds/BuildContentLinksUsing";
import BuildContentLinksUsedBy from "@components/builds/BuildContentLinksUsedBy";
import BuildContentNotifications from "@components/builds/BuildContentNotifications";
import BuildContentPromotions from "@components/builds/BuildContentPromotions";
import {UserContext} from "@components/providers/UserProvider";
import {useContext} from "react";
import BuildContentEnvironments from "@components/builds/BuildContentEnvironments";
import GridTableContextProvider from "@components/grid/GridTableContext";
import GridTable from "@components/grid/GridTable";

export default function BuildContent({build}) {

    const user = useContext(UserContext)

    let defaultLayout
    if (user.authorizations.environment?.view) {
        defaultLayout = [
            {i: "promotions", x: 0, y: 0, w: 5, h: 6},
            // Since #1794 the cell is a single wrapping strip of chips, not a timeline of
            // expandable rows, so it needs a fraction of the height it used to. The grid compacts
            // vertically, so the rows below close the gap on their own.
            {i: "environments", x: 0, y: 6, w: 5, h: 4},
            {i: "validations", x: 5, y: 0, w: 7, h: 15},
            {i: "using", x: 0, y: 15, w: 6, h: 9},
            {i: "usedBy", x: 6, y: 24, w: 6, h: 9},
            {i: "notifications", x: 0, y: 33, w: 12, h: 9},
        ]
    } else {
        defaultLayout = [
            {i: "promotions", x: 0, y: 0, w: 4, h: 9},
            {i: "validations", x: 6, y: 0, w: 8, h: 9},
            {i: "using", x: 0, y: 9, w: 6, h: 9},
            {i: "usedBy", x: 6, y: 9, w: 6, h: 9},
            {i: "notifications", x: 0, y: 18, w: 12, h: 9},
        ]
    }

    const items = [
        {
            id: "promotions",
            content: <BuildContentPromotions
                build={build}
            />,
        },
        {
            id: "validations",
            content: <BuildContentValidations
                build={build}
            />,
        },
        {
            id: "using",
            content: <BuildContentLinksUsing
                build={build}
            />,
        },
        {
            id: "usedBy",
            content: <BuildContentLinksUsedBy
                build={build}
            />,
        },
        {
            id: "notifications",
            content: <BuildContentNotifications
                build={build}
            />,
        },
    ]

    if (user.authorizations.environment?.view) {
        items.push({
            id: "environments",
            content: <BuildContentEnvironments
                build={build}
            />,
        })
    }

    return (
        <>
            <GridTableContextProvider isDraggable={false}>
                <GridTable
                    rowHeight={30}
                    layout={defaultLayout}
                    items={items}
                    isResizable={false}
                />
            </GridTableContextProvider>
        </>
    )
}
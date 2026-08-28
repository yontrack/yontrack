import "@testing-library/jest-dom"
import {render} from "@testing-library/react"
import GridCell from "@components/grid/GridCell"
import {GridTableContext} from "@components/grid/GridTableContext"

const HANDLE = '.ot-rgl-draggable-handle'

const renderCell = ({draggable, ...props}) => {
    const context = {
        expandable: false,
        draggable,
        setExpandable: () => {
        },
        expandedId: '',
        toggleExpandedId: () => {
        },
        clearExpandedId: () => {
        },
    }
    const {container} = render(
        <GridTableContext.Provider value={context}>
            <GridCell id="widget" title="Widget" {...props}/>
        </GridTableContext.Provider>
    )
    return container.querySelector(HANDLE)
}

describe('GridCell', () => {

    describe('drag handle', () => {

        it('is hidden when the cell is not draggable, even inside a draggable grid', () => {
            // Regression for #1641: a dashboard outside edition mode declares its cells
            // non-draggable while the grid keeps its draggable default. The handle used to
            // be displayed - and did nothing, the grid itself not being draggable.
            expect(renderCell({draggable: true, isDraggable: false})).not.toBeInTheDocument()
        })

        it('is displayed when the cell is draggable', () => {
            expect(renderCell({draggable: false, isDraggable: true})).toBeInTheDocument()
        })

        it('follows the grid when the cell states nothing', () => {
            expect(renderCell({draggable: true})).toBeInTheDocument()
            expect(renderCell({draggable: false})).not.toBeInTheDocument()
        })
    })
})

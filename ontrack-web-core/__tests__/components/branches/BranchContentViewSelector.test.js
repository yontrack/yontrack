import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"
import {FaListUl, FaStream} from "react-icons/fa"
import BranchContentViewSelector from "@components/branches/BranchContentViewSelector"

const views = [
    {key: 'builds', name: "Builds", icon: <FaListUl/>},
    {key: 'pipeline', name: "Pipeline", icon: <FaStream/>, experimental: true},
]

const openMenu = async () => {
    fireEvent.click(screen.getByRole('button', {name: /View/}))
    // The menu is rendered in a portal, only once the dropdown is open
    await screen.findByText("Pipeline")
}

describe('BranchContentViewSelector', () => {

    it('lists every registered view', async () => {
        render(<BranchContentViewSelector views={views} selectedViewKey="builds" onSelect={jest.fn()}/>)
        await openMenu()
        expect(screen.getByText("Builds")).toBeInTheDocument()
        expect(screen.getByText("Pipeline")).toBeInTheDocument()
    })

    it('reports the key of the view the user picks', async () => {
        const onSelect = jest.fn()
        render(<BranchContentViewSelector views={views} selectedViewKey="builds" onSelect={onSelect}/>)
        await openMenu()
        fireEvent.click(screen.getByText("Pipeline"))
        expect(onSelect).toHaveBeenCalledWith('pipeline')
    })

    it('marks the current view as selected', async () => {
        render(<BranchContentViewSelector views={views} selectedViewKey="pipeline" onSelect={jest.fn()}/>)
        await openMenu()
        const selected = document.querySelectorAll('.ant-dropdown-menu-item-selected')
        expect(selected).toHaveLength(1)
        expect(selected[0]).toHaveTextContent("Pipeline")
    })

    it('names the menu for the UI tests to find', async () => {
        render(<BranchContentViewSelector views={views} selectedViewKey="builds" onSelect={jest.fn()}/>)
        await openMenu()
        expect(screen.getByTestId('branch-content-views')).toBeInTheDocument()
    })

    describe('experimental marker', () => {

        it('marks an entry which declares itself experimental', async () => {
            render(<BranchContentViewSelector views={views} selectedViewKey="builds" onSelect={jest.fn()}/>)
            await openMenu()
            expect(screen.getByTestId('branch-content-view-experimental-pipeline')).toBeInTheDocument()
        })

        it('leaves an entry which does not declare itself experimental unmarked', async () => {
            render(<BranchContentViewSelector views={views} selectedViewKey="builds" onSelect={jest.fn()}/>)
            await openMenu()
            expect(screen.queryByTestId('branch-content-view-experimental-builds')).not.toBeInTheDocument()
        })

        it('goes by the flag, not by the key of the view', async () => {
            // The registry entry shape is one a server-driven list could later populate, so the
            // selector must not know which key happens to be experimental today
            const flagged = [
                {key: 'builds', name: "Builds", icon: <FaListUl/>, experimental: true},
                {key: 'pipeline', name: "Pipeline", icon: <FaStream/>},
            ]
            render(<BranchContentViewSelector views={flagged} selectedViewKey="builds" onSelect={jest.fn()}/>)
            await openMenu()
            expect(screen.getByTestId('branch-content-view-experimental-builds')).toBeInTheDocument()
            expect(screen.queryByTestId('branch-content-view-experimental-pipeline')).not.toBeInTheDocument()
        })

        it('leaves the accessible name of the item to the view name alone', async () => {
            // The UI tests address the menu items by their accessible name; a badge added inside
            // the label must not become part of it
            render(<BranchContentViewSelector views={views} selectedViewKey="builds" onSelect={jest.fn()}/>)
            await openMenu()
            expect(screen.getByRole('menuitem', {name: "Pipeline"})).toBeInTheDocument()
        })

        it('still reports the key of an experimental view the user picks', async () => {
            const onSelect = jest.fn()
            render(<BranchContentViewSelector views={views} selectedViewKey="builds" onSelect={onSelect}/>)
            await openMenu()
            fireEvent.click(screen.getByText("Pipeline"))
            expect(onSelect).toHaveBeenCalledWith('pipeline')
        })

    })

    it('renders nothing when there is no view to choose from', () => {
        const {container} = render(<BranchContentViewSelector views={[]} selectedViewKey="builds" onSelect={jest.fn()}/>)
        expect(container).toBeEmptyDOMElement()
    })

})

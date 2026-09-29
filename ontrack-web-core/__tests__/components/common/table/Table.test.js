import React from "react";
import {render, screen} from "@testing-library/react";
import '@testing-library/jest-dom';

import Table, {DEFAULT_STICKY} from "@components/common/table/Table";

// Ant Design uses window.matchMedia for responsive features; jsdom doesn't provide it
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
})

const columns = [{key: 'name', dataIndex: 'name', title: 'Name'}]
const dataSource = [{key: '1', name: 'One'}, {key: '2', name: 'Two'}]

// antd renders a sticky header in a holder of its own, split from the body
const stickyHolder = (container) => container.querySelector('.ant-table-sticky-holder')

describe('Table', () => {

    it('renders the rows of an antd table', () => {
        render(<Table columns={columns} dataSource={dataSource} pagination={false}/>)
        expect(screen.getByRole('columnheader', {name: 'Name'})).toBeInTheDocument()
        expect(screen.getByText('One')).toBeInTheDocument()
        expect(screen.getByText('Two')).toBeInTheDocument()
    })

    it('sticks its header by default when the default is on', () => {
        const {container} = render(<Table columns={columns} dataSource={dataSource} pagination={false}/>)
        if (DEFAULT_STICKY) {
            expect(stickyHolder(container)).toBeInTheDocument()
        } else {
            expect(stickyHolder(container)).not.toBeInTheDocument()
        }
    })

    it('does not stick its header when the caller opts out', () => {
        const {container} = render(<Table columns={columns} dataSource={dataSource} pagination={false}
                                          sticky={false}/>)
        expect(stickyHolder(container)).not.toBeInTheDocument()
    })

    it('sticks its header with the options given by the caller', () => {
        const {container} = render(<Table columns={columns} dataSource={dataSource} pagination={false}
                                          sticky={{offsetHeader: 12}}/>)
        const holder = stickyHolder(container)
        expect(holder).toBeInTheDocument()
        expect(holder).toHaveStyle({top: '12px'})
    })

    it('accepts columns declared as Table.Column children', () => {
        render(
            <Table dataSource={dataSource} pagination={false}>
                <Table.Column key="name" dataIndex="name" title="Name"/>
            </Table>
        )
        expect(screen.getByRole('columnheader', {name: 'Name'})).toBeInTheDocument()
        expect(screen.getByText('One')).toBeInTheDocument()
    })

    it('exposes the static members of the antd table', () => {
        expect(Table.Column).toBeDefined()
        expect(Table.ColumnGroup).toBeDefined()
        expect(Table.Summary).toBeDefined()
        expect(Table.SELECTION_COLUMN).toBeDefined()
        expect(Table.EXPAND_COLUMN).toBeDefined()
    })

})

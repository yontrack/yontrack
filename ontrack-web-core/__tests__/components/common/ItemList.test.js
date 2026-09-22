import React from "react";
import {render, screen, within} from "@testing-library/react";
import '@testing-library/jest-dom';

import ItemList from "@components/common/ItemList";

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

describe('ItemList', () => {

    it('renders a list of list items', () => {
        render(
            <ItemList>
                <ItemList.Item title="One"/>
                <ItemList.Item title="Two"/>
            </ItemList>
        )
        const list = screen.getByRole('list')
        const items = within(list).getAllByRole('listitem')
        expect(items).toHaveLength(2)
        expect(items[0]).toHaveTextContent('One')
        expect(items[1]).toHaveTextContent('Two')
    })

    it('renders the avatar, title and description of an item', () => {
        render(
            <ItemList>
                <ItemList.Item
                    avatar={<span data-testid="avatar">A</span>}
                    title="The title"
                    description="The description"
                />
            </ItemList>
        )
        const item = screen.getByRole('listitem')
        expect(within(item).getByTestId('avatar')).toBeInTheDocument()
        expect(item).toHaveTextContent('The title')
        expect(item).toHaveTextContent('The description')
    })

    it('renders the children of an item', () => {
        render(
            <ItemList>
                <ItemList.Item><strong>Custom</strong></ItemList.Item>
            </ItemList>
        )
        expect(within(screen.getByRole('listitem')).getByText('Custom')).toBeInTheDocument()
    })

    it('renders the actions of an item', () => {
        render(
            <ItemList>
                <ItemList.Item
                    title="Item"
                    actions={[
                        <button key="edit">Edit</button>,
                        <button key="delete">Delete</button>,
                    ]}
                />
            </ItemList>
        )
        const item = screen.getByRole('listitem')
        expect(within(item).getByRole('button', {name: 'Edit'})).toBeInTheDocument()
        expect(within(item).getByRole('button', {name: 'Delete'})).toBeInTheDocument()
    })

    it('renders the empty text when there is no item', () => {
        render(<ItemList emptyText="Nothing here" data-testid="list"/>)
        expect(screen.getByTestId('list')).toHaveTextContent('Nothing here')
        expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
    })

    it('ignores null children when deciding whether it is empty', () => {
        render(
            <ItemList emptyText="Nothing here">
                {null}
                {false && <ItemList.Item title="Hidden"/>}
            </ItemList>
        )
        expect(screen.getByText('Nothing here')).toBeInTheDocument()
    })

    it('renders a default empty state without an empty text', () => {
        render(<ItemList data-testid="list"/>)
        expect(screen.getByTestId('list')).toHaveTextContent('No data')
    })

    it('passes the data-testid through to the list and to its items', () => {
        render(
            <ItemList data-testid="the-list" size="small">
                <ItemList.Item data-testid="the-item" title="Item"/>
            </ItemList>
        )
        expect(screen.getByTestId('the-list').tagName).toBe('UL')
        expect(screen.getByTestId('the-item').tagName).toBe('LI')
    })

    it('renders its list through another component', () => {
        const Container = ({as: Tag, children, ...rest}) => <Tag data-container="yes" {...rest}>{children}</Tag>
        render(
            <ItemList component={Container} as="ul">
                <ItemList.Item title="Item"/>
            </ItemList>
        )
        const list = screen.getByRole('list')
        expect(list).toHaveAttribute('data-container', 'yes')
        expect(within(list).getByRole('listitem')).toHaveTextContent('Item')
    })

})

import React from "react";
import {act, fireEvent, render, screen} from "@testing-library/react";
import '@testing-library/jest-dom';

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

// The sections fetch or render dynamic content - replaced by probes which expose
// what the drawer passes to them
let reportProperties = null
jest.mock("../../../components/framework/properties/PropertiesSection", () => function PropertiesSection(props) {
    reportProperties = props.onPropertiesLoaded
    return <div data-testid="properties-section">{props.entityType}</div>
})
jest.mock("../../../components/framework/information/InformationSection", () => function InformationSection() {
    return <div data-testid="information-section"/>
})

import InfoViewDrawer, {hasEntityDetails} from "../../../components/common/InfoViewDrawer";

const unset = {type: {typeName: 'general.MessagePropertyType'}, value: null}
const set = {type: {typeName: 'general.ReleasePropertyType'}, value: {name: '1.0.0'}}

describe('hasEntityDetails', () => {

    it('is false when nothing is set', () => {
        expect(hasEntityDetails({properties: [unset], information: []})).toBe(false)
    })

    it('is false when the entity carries neither properties nor information', () => {
        expect(hasEntityDetails({})).toBe(false)
    })

    it('is true when one property has a value', () => {
        expect(hasEntityDetails({properties: [unset, set], information: []})).toBe(true)
    })

    it('is true when there is one information entry', () => {
        expect(hasEntityDetails({properties: [unset], information: [{type: 'x', data: {}}]})).toBe(true)
    })

    it('ignores information entries without data', () => {
        expect(hasEntityDetails({properties: [], information: [{type: 'x', data: null}]})).toBe(false)
    })
})

describe('InfoViewDrawer', () => {

    const renderDrawer = (entity) => render(
        <InfoViewDrawer
            id="build-info"
            entityType="BUILD"
            entityName="build"
            entity={entity}
        />
    )

    it('is a "Details" command describing what it opens', () => {
        renderDrawer({id: 1, properties: [unset], information: []})
        const command = screen.getByTestId('build-info')
        expect(command).toHaveTextContent('Details')
        expect(command).toHaveAttribute('title', 'Properties and additional information about this build')
    })

    it('shows no dot when nothing is set', () => {
        renderDrawer({id: 1, properties: [unset], information: []})
        expect(screen.queryByTestId('build-info-dot')).not.toBeInTheDocument()
    })

    it('shows a dot when a property is set', () => {
        renderDrawer({id: 1, properties: [set], information: []})
        expect(screen.getByTestId('build-info-dot')).toBeInTheDocument()
    })

    it('opens the drawer with an entity specific title', async () => {
        renderDrawer({id: 1, properties: [], information: []})
        fireEvent.click(screen.getByTestId('build-info'))
        expect(await screen.findByText('Build details')).toBeInTheDocument()
        expect(screen.getByTestId('properties-section')).toHaveTextContent('BUILD')
    })

    it('refreshes the dot from the properties reloaded by the drawer', async () => {
        renderDrawer({id: 1, properties: [], information: []})
        fireEvent.click(screen.getByTestId('build-info'))
        await screen.findByText('Build details')
        expect(screen.queryByTestId('build-info-dot')).not.toBeInTheDocument()

        act(() => reportProperties([set]))
        expect(screen.getByTestId('build-info-dot')).toBeInTheDocument()

        act(() => reportProperties([unset]))
        expect(screen.queryByTestId('build-info-dot')).not.toBeInTheDocument()
    })

    it('does not carry reloaded properties over to another entity', async () => {
        const {rerender} = renderDrawer({id: 1, properties: [], information: []})
        fireEvent.click(screen.getByTestId('build-info'))
        await screen.findByText('Build details')
        act(() => reportProperties([set]))
        expect(screen.getByTestId('build-info-dot')).toBeInTheDocument()

        rerender(
            <InfoViewDrawer
                id="build-info"
                entityType="BUILD"
                entityName="build"
                entity={{id: 2, properties: [], information: []}}
            />
        )
        expect(screen.queryByTestId('build-info-dot')).not.toBeInTheDocument()
    })
})

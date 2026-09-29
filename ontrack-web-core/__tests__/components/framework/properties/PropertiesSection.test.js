import React from "react";
import {act, render, screen, waitFor} from "@testing-library/react";
import '@testing-library/jest-dom';
import PropertiesSection from "@components/framework/properties/PropertiesSection";

let mockRefreshCount = 0

jest.mock("../../../../components/common/EventsContext", () => ({
    useEventForRefresh: () => mockRefreshCount,
}))

// Every property prepares its value by upper-casing it
jest.mock("../../../../components/common/DynamicFunction", () => ({
    callDynamicFunction: async (moduleName, value) => ({prepared: value.text.toUpperCase()}),
}))

jest.mock("../../../../components/common/ListSection", () => function ListSection({items}) {
    return (
        <ul>
            {items.map(item => <li key={item.id}>{item.content}</li>)}
        </ul>
    )
})

jest.mock("../../../../components/framework/properties/PropertyComponent", () => function PropertyComponent({property}) {
    return <span>{property.clientValue.prepared}</span>
})

jest.mock("../../../../components/core/model/properties/PropertyAddButton", () => () => null)
jest.mock("../../../../components/framework/properties/PropertyIcon", () => () => null)
jest.mock("../../../../components/framework/properties/PropertyTitle", () => () => null)

/**
 * Holds each GraphQL call pending until the test answers it with `pending[n](body)`.
 */
const mockGraphQL = () => {
    const pending = []
    global.fetch = jest.fn().mockImplementation(() => new Promise(resolve => {
        pending.push((body) => resolve({
            ok: true,
            status: 200,
            json: async () => body,
        }))
    }))
    return pending
}

const property = (name, text) => ({
    editable: true,
    type: {
        typeName: `net.nemerosa.ontrack.extension.general.${name}PropertyType`,
        name,
        description: '',
    },
    value: text ? {text} : null,
})

describe('PropertiesSection', () => {

    afterEach(() => {
        delete global.fetch
        mockRefreshCount = 0
    })

    it('loads the properties, prepares their values and notifies the loading', async () => {
        const pending = mockGraphQL()
        const onPropertiesLoaded = jest.fn()

        render(<PropertiesSection entityType="BUILD" entityId="10" onPropertiesLoaded={onPropertiesLoaded}/>)

        await waitFor(() => expect(pending).toHaveLength(1))
        const request = JSON.parse(global.fetch.mock.calls[0][1].body)
        expect(request.variables).toEqual({type: 'BUILD', id: 10})
        await act(async () => pending[0]({
            entity: {
                properties: [
                    property('Zeta', 'last'),
                    property('Empty', null),
                    property('Alpha', 'first'),
                ]
            }
        }))

        await waitFor(() => expect(onPropertiesLoaded).toHaveBeenCalledTimes(1))
        const loaded = onPropertiesLoaded.mock.calls[0][0]
        // Sorted by name, only the set ones being prepared
        expect(loaded.map(it => it.type.name)).toEqual(['Alpha', 'Empty', 'Zeta'])
        expect(loaded[0].shortName).toBe('general.AlphaPropertyType')
        expect(loaded[0].clientValue).toEqual({prepared: 'FIRST'})
        expect(loaded[1].clientValue).toBeUndefined()

        const items = screen.getAllByRole('listitem')
        expect(items.map(it => it.textContent)).toEqual(['FIRST', 'LAST'])
    })

    it('reloads the properties when they are changed', async () => {
        const pending = mockGraphQL()
        const onPropertiesLoaded = jest.fn()

        const {rerender} = render(<PropertiesSection entityType="BUILD" entityId="10" onPropertiesLoaded={onPropertiesLoaded}/>)
        await waitFor(() => expect(pending).toHaveLength(1))
        await act(async () => pending[0]({entity: {properties: [property('Alpha', 'first')]}}))
        await waitFor(() => expect(onPropertiesLoaded).toHaveBeenCalledTimes(1))

        mockRefreshCount = 1
        rerender(<PropertiesSection entityType="BUILD" entityId="10" onPropertiesLoaded={onPropertiesLoaded}/>)
        await waitFor(() => expect(pending).toHaveLength(2))
        await act(async () => pending[1]({entity: {properties: [property('Alpha', 'second')]}}))

        await waitFor(() => expect(onPropertiesLoaded).toHaveBeenCalledTimes(2))
        expect(screen.getByRole('listitem')).toHaveTextContent('SECOND')
    })

})

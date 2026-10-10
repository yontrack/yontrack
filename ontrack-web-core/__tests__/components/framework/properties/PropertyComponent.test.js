import React from "react";
import {render, screen} from "@testing-library/react";
import '@testing-library/jest-dom';
import PropertyComponent from "@components/framework/properties/PropertyComponent";

jest.mock("../../../../components/common/Dynamic", () => ({
    Dynamic: ({path}) => <span data-testid="dynamic">{path}</span>,
}))

const type = {
    typeName: 'net.nemerosa.ontrack.extension.general.MessagePropertyType',
    name: 'Message',
}

describe('PropertyComponent', () => {

    it('displays the value through the display component of the property type', () => {
        render(<PropertyComponent property={{type, value: {text: 'Hello'}}}/>)
        expect(screen.getByTestId('dynamic')).toHaveTextContent('framework/properties/general.MessagePropertyType/Display')
        expect(screen.queryByTestId('property-error')).not.toBeInTheDocument()
    })

    it('displays the error of a property whose value cannot be read', () => {
        render(<PropertyComponent property={{type, value: null, error: 'Cannot read authType'}}/>)
        expect(screen.getByTestId('property-error')).toHaveTextContent('Cannot read authType')
        expect(screen.queryByTestId('dynamic')).not.toBeInTheDocument()
    })

})

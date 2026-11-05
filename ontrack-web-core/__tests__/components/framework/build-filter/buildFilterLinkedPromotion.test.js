import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"
import {Form} from "antd"
import StandardBuildFilterProvider, {
    StandardBuildFilterFields,
} from "@components/framework/build-filter/net.nemerosa.ontrack.service.StandardBuildFilterProvider"

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

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: () => ({data: [], loading: false, finished: true}),
}))

const branch = {id: 1, name: 'main'}

/**
 * The form, on its Links tab. The promotion of a linked build is the one of another project: the branch
 * of the filter cannot list it, so it is typed.
 */
const openLinks = (content) => {
    let form
    const Wrapper = () => {
        [form] = Form.useForm()
        return <Form form={form}>{content}</Form>
    }
    render(<Wrapper/>)
    fireEvent.click(screen.getByRole('tab', {name: 'Links'}))
    return () => form.getFieldsValue(true).data
}

describe.each([
    ['the Classic form', <StandardBuildFilterProvider key="classic" branch={branch} buildFilterForm={{typeName: 'Standard filter'}}/>],
    ['the form of a boundary', <StandardBuildFilterFields key="boundary" branches={[branch]} singleBuild={true}/>],
])('The promotions of the linked builds in %s', (_, content) => {

    it('are typed, since they are the ones of another project', () => {
        const values = openLinks(content)
        const [from, to] = screen.getAllByLabelText('... with promotion')
        expect(from).toHaveAttribute('type', 'text')
        expect(to).toHaveAttribute('type', 'text')
        fireEvent.change(from, {target: {value: 'GOLD'}})
        fireEvent.change(to, {target: {value: 'SILVER'}})
        expect(values()).toMatchObject({linkedFromPromotion: 'GOLD', linkedToPromotion: 'SILVER'})
    })
})

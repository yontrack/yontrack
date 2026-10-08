import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import AccountKindTag from "@components/common/actors/AccountKindTag"

describe('AccountKindTag', () => {

    it('says "Agent" in words for an agent, beside a robot icon', () => {
        render(<AccountKindTag kind="AGENT" testId="kind"/>)
        const tag = screen.getByTestId("kind")
        expect(tag).toHaveTextContent(/^Agent$/)
        expect(tag).toHaveClass("ant-tag-purple")
        expect(tag.querySelector("svg")).toHaveAttribute("aria-hidden", "true")
    })

    it('says "Person" in words for a person', () => {
        render(<AccountKindTag kind="HUMAN" testId="kind"/>)
        const tag = screen.getByTestId("kind")
        expect(tag).toHaveTextContent(/^Person$/)
        expect(tag).not.toHaveClass("ant-tag-purple")
    })

    it('renders nothing for an unknown kind', () => {
        expect(render(<AccountKindTag/>).container).toBeEmptyDOMElement()
    })
})

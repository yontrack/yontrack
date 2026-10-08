import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import Display from "@components/framework/properties/agents.evidence.NonAgentEvidencePropertyType/Display"
import FormPrepare from "@components/framework/properties/agents.evidence.NonAgentEvidencePropertyType/FormPrepare"
import {useLicensedFeature} from "@components/extension/license/useLicensedFeature"

jest.mock("../../../../components/extension/license/useLicensedFeature", () => ({
    useLicensedFeature: jest.fn(),
}))

const property = (value) => ({value})

describe("Evidence from non-agents only property", () => {

    beforeEach(() => {
        useLicensedFeature.mockReturnValue({enabled: true, loading: false})
    })

    it("says that evidence must come from a non-agent actor", () => {
        render(<Display property={property({enabled: true})}/>)
        expect(screen.getByTestId("non-agent-evidence")).toHaveTextContent("must come from a non-agent actor")
        expect(screen.queryByTestId("non-agent-evidence-licence")).not.toBeInTheDocument()
    })

    it("says when the restriction is switched off", () => {
        render(<Display property={property({enabled: false})}/>)
        expect(screen.getByTestId("non-agent-evidence-off")).toBeInTheDocument()
    })

    it("says the licence is needed when it does not allow the agent governance", () => {
        useLicensedFeature.mockReturnValue({enabled: false, loading: false})
        render(<Display property={property({enabled: true})}/>)
        expect(screen.getByTestId("non-agent-evidence-licence"))
            .toHaveTextContent("Requires the Agent governance licence")
        expect(useLicensedFeature).toHaveBeenCalledWith("extension.agents")
    })

    it("says nothing about the licence while it is not known", () => {
        useLicensedFeature.mockReturnValue({enabled: undefined, loading: true})
        render(<Display property={property({enabled: true})}/>)
        expect(screen.queryByTestId("non-agent-evidence-licence")).not.toBeInTheDocument()
    })

    it("prepares an enabled restriction unless switched off", () => {
        expect(FormPrepare({})).toEqual({enabled: true})
        expect(FormPrepare({enabled: false})).toEqual({enabled: false})
    })
})

import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import Display from "@components/framework/properties/agents.assisted.AssistedBuildsRequirePropertyType/Display"
import FormPrepare from "@components/framework/properties/agents.assisted.AssistedBuildsRequirePropertyType/FormPrepare"
import {useLicensedFeature} from "@components/extension/license/useLicensedFeature"

jest.mock("../../../../components/extension/license/useLicensedFeature", () => ({
    useLicensedFeature: jest.fn(),
}))

const property = (value) => ({value})

describe("Assisted builds require property", () => {

    beforeEach(() => {
        useLicensedFeature.mockReturnValue({enabled: true, loading: false})
    })

    it("lists the stamps required of assisted builds, and the fail-closed rule", () => {
        render(<Display property={property({validationStamps: ["REVIEW", "SCAN"]})}/>)
        expect(screen.getByText("If the build is assisted, these validations must pass first:")).toBeInTheDocument()
        expect(screen.getByTestId("assisted-builds-require-stamps")).toHaveTextContent("REVIEW")
        expect(screen.getByTestId("assisted-builds-require-stamps")).toHaveTextContent("SCAN")
        expect(screen.getByText(/counts as assisted/)).toBeInTheDocument()
        expect(screen.queryByTestId("assisted-builds-require-licence")).not.toBeInTheDocument()
    })

    it("says when nothing is required", () => {
        render(<Display property={property({validationStamps: []})}/>)
        expect(screen.getByTestId("assisted-builds-require-none")).toBeInTheDocument()
    })

    it("says the licence is needed when it does not allow the agent governance", () => {
        useLicensedFeature.mockReturnValue({enabled: false, loading: false})
        render(<Display property={property({validationStamps: ["REVIEW"]})}/>)
        expect(screen.getByTestId("assisted-builds-require-licence"))
            .toHaveTextContent("Requires the Agent governance licence")
        expect(useLicensedFeature).toHaveBeenCalledWith("extension.agents")
    })

    it("says nothing about the licence while it is not known", () => {
        useLicensedFeature.mockReturnValue({enabled: undefined, loading: true})
        render(<Display property={property({validationStamps: ["REVIEW"]})}/>)
        expect(screen.queryByTestId("assisted-builds-require-licence")).not.toBeInTheDocument()
    })

    it("prepares an empty list when no stamp is selected", () => {
        expect(FormPrepare({})).toEqual({validationStamps: []})
        expect(FormPrepare({validationStamps: ["REVIEW"]})).toEqual({validationStamps: ["REVIEW"]})
    })
})

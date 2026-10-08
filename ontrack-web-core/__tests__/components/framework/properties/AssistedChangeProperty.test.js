import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import Display from "@components/framework/properties/scm.changelog.assistants.AssistedChangePropertyType/Display"
import FormPrepare from "@components/framework/properties/scm.changelog.assistants.AssistedChangePropertyType/FormPrepare"

const property = (value) => ({value})

describe("Assisted change property", () => {

    it("shows the assistants, the counts, the basis and the session links of an assisted build", () => {
        render(<Display property={property({
            basis: "COMPUTED",
            unknownReason: null,
            assistants: ["Claude Code", "Codex"],
            assistedCommits: 2,
            totalCommits: 5,
            sessionLinks: ["https://claude.ai/code/session_1"],
            previousBuildId: 10,
        })}/>)
        expect(screen.getByText("Assisted by Claude Code, Codex (2 of 5 commits)")).toBeInTheDocument()
        expect(screen.getByTestId("assisted-change-basis")).toHaveTextContent("computed from the change log")
        const link = screen.getByRole("link", {name: /Agent session/})
        expect(link).toHaveAttribute("href", "https://claude.ai/code/session_1")
        expect(link).toHaveAttribute("target", "_blank")
        expect(link).toHaveAttribute("rel", "noopener noreferrer")
    })

    it("says when a build is not assisted", () => {
        render(<Display property={property({
            basis: "SET_BY_CI",
            assistants: [],
            assistedCommits: 0,
            totalCommits: 3,
            sessionLinks: [],
        })}/>)
        expect(screen.getByTestId("assisted-change-not-assisted")).toHaveTextContent("Not assisted (0 of 3 commits)")
        expect(screen.getByTestId("assisted-change-basis")).toHaveTextContent("set by the CI")
        expect(screen.queryByRole("link")).not.toBeInTheDocument()
    })

    it("gives the reason of an unknown value", () => {
        render(<Display property={property({
            basis: "UNKNOWN",
            unknownReason: "no previous build with a commit",
            assistants: [],
            assistedCommits: 0,
            totalCommits: 0,
            sessionLinks: [],
        })}/>)
        expect(screen.getByTestId("assisted-change-unknown")).toHaveTextContent("Unknown: no previous build with a commit")
    })

    it("sets the value from the form like the CI", () => {
        expect(FormPrepare({assistants: ["Codex"], assistedCommits: 1, totalCommits: 2})).toEqual({
            basis: "SET_BY_CI",
            assistants: ["Codex"],
            assistedCommits: 1,
            totalCommits: 2,
            sessionLinks: [],
        })
    })
})

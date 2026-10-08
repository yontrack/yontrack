import {
    assistedBasisText,
    assistedCommitsText,
    hasAgentActions,
    hasAgentsSection,
} from "@components/builds/agents/buildAgents"

const assisted = {
    assisted: true,
    basis: "COMPUTED",
    unknownReason: null,
    assistants: ["Claude Code"],
    assistedCommits: 3,
    totalCommits: 12,
    sessionLinks: [],
    previousBuild: {id: 41, name: "41", displayName: "1.0.41"},
}

const noAction = {pageInfo: {nextPage: null}, pageItems: []}
const oneAction = {pageInfo: {nextPage: null}, pageItems: [{id: 1}]}

describe('Whether a build has an Agents section', () => {

    it('is shown for an assisted build', () => {
        expect(hasAgentsSection({assistedChange: assisted, agentActionsProbe: noAction})).toBe(true)
    })

    it('is shown for a build an agent acted on', () => {
        expect(hasAgentsSection({assistedChange: null, agentActionsProbe: oneAction})).toBe(true)
    })

    it('is shown when the first action is left out but there are more', () => {
        expect(hasAgentActions({pageInfo: {nextPage: {offset: 1}}, pageItems: []})).toBe(true)
    })

    it('is not shown when there is nothing to show', () => {
        expect(hasAgentsSection({assistedChange: null, agentActionsProbe: noAction})).toBe(false)
        expect(hasAgentsSection({assistedChange: {...assisted, assisted: false, assistants: []}, agentActionsProbe: noAction})).toBe(false)
        expect(hasAgentsSection({})).toBe(false)
        expect(hasAgentsSection(undefined)).toBe(false)
    })

    it('is not shown for an unknown assisted change alone - the header badge says it', () => {
        expect(hasAgentsSection({
            assistedChange: {assisted: false, basis: "UNKNOWN", unknownReason: "no SCM", assistants: []},
            agentActionsProbe: noAction,
        })).toBe(false)
    })
})

describe('Basis of an assisted change', () => {

    it('says how it was obtained', () => {
        expect(assistedBasisText(assisted)).toBe("Computed by Yontrack from the change log")
        expect(assistedBasisText({...assisted, basis: "SET_BY_CI"})).toBe("Set by the CI")
    })

    it('gives the reason of an unknown one', () => {
        expect(assistedBasisText({basis: "UNKNOWN", unknownReason: "no previous build with a commit"}))
            .toBe("Unknown: no previous build with a commit")
        expect(assistedBasisText({basis: "UNKNOWN", unknownReason: null})).toBe("Unknown")
    })

    it('says when it is not computed yet', () => {
        expect(assistedBasisText(null)).toBe("Not computed yet")
    })
})

describe('Commits of an assisted change', () => {

    it('counts the commits since the previous build', () => {
        expect(assistedCommitsText(assisted)).toBe("3 of 12 commits since 1.0.41")
    })

    it('uses the name of the previous build when it has no display name', () => {
        expect(assistedCommitsText({...assisted, previousBuild: {id: 41, name: "41"}})).toBe("3 of 12 commits since 41")
    })

    it('says "commit" for one commit', () => {
        expect(assistedCommitsText({...assisted, assistedCommits: 1, totalCommits: 1})).toBe("1 of 1 commit since 1.0.41")
    })

    it('counts the commits alone without a previous build', () => {
        expect(assistedCommitsText({...assisted, previousBuild: null})).toBe("3 of 12 commits")
    })

    it('says nothing when the CI gave no counts', () => {
        expect(assistedCommitsText({...assisted, basis: "SET_BY_CI", assistedCommits: null, totalCommits: null})).toBeNull()
        expect(assistedCommitsText(null)).toBeNull()
    })
})

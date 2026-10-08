import {
    buildActorOptions,
    buildAssistedOptions,
    typedAgentIdentifier,
} from "@components/framework/build-filter/buildFilterAgents"

describe('buildAssistedOptions', () => {
    it('offers the three values of the assisted criterion', () => {
        expect(buildAssistedOptions.map(option => option.value)).toEqual(["YES", "NO", "UNKNOWN"])
    })
})

describe('typedAgentIdentifier', () => {
    it('gives nothing for a blank text', () => {
        expect(typedAgentIdentifier(undefined)).toBeUndefined()
        expect(typedAgentIdentifier("  ")).toBeUndefined()
    })

    it('adds the suffix to a slug', () => {
        expect(typedAgentIdentifier("claude")).toBe("claude[agent]")
    })

    it('keeps a full identifier, in lowercase', () => {
        expect(typedAgentIdentifier(" Claude-2[AGENT] ")).toBe("claude-2[agent]")
    })

    it('gives nothing for a text which cannot be an agent', () => {
        expect(typedAgentIdentifier("damien@yontrack.test")).toBeUndefined()
        expect(typedAgentIdentifier("not a slug")).toBeUndefined()
    })
})

describe('buildActorOptions', () => {
    const agents = [
        {email: "claude[agent]", fullName: "Claude"},
        {email: "codex[agent]", fullName: "Codex"},
    ]

    it('offers the persons and the agents when no agent is visible', () => {
        expect(buildActorOptions([])).toEqual([
            {value: "HUMAN", label: "Humans"},
            {value: "AGENT", label: "Agents"},
        ])
    })

    it('offers each visible agent by its identifier', () => {
        expect(buildActorOptions(agents)).toEqual([
            {value: "HUMAN", label: "Humans"},
            {value: "AGENT", label: "Agents"},
            {
                label: "Agent",
                title: "Agent",
                options: [
                    {value: "claude[agent]", label: "Claude (claude[agent])"},
                    {value: "codex[agent]", label: "Codex (codex[agent])"},
                ],
            },
        ])
    })

    it('offers the identifier of an agent being typed', () => {
        const options = buildActorOptions(agents, "devin")
        expect(options[2].options.map(option => option.value))
            .toEqual(["claude[agent]", "codex[agent]", "devin[agent]"])
    })

    it('does not repeat a visible agent being typed', () => {
        const options = buildActorOptions(agents, "claude")
        expect(options[2].options.map(option => option.value)).toEqual(["claude[agent]", "codex[agent]"])
    })

    it('keeps the selected agent as an option when it is not visible', () => {
        const options = buildActorOptions([], "", "devin[agent]")
        expect(options[2].options).toEqual([{value: "devin[agent]", label: "devin[agent]"}])
    })

    it('does not repeat the persons nor the agents as a selected agent', () => {
        expect(buildActorOptions([], "", "AGENT")).toHaveLength(2)
    })
})

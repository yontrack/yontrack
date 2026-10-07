import {AGENT_SLUG_PATTERN, agentLastUsed, agentSlug} from "@components/core/admin/agents/agentsModel";

describe('agentsModel', () => {

    test('valid slugs', () => {
        ['a', 'claude-code', 'codex-2', 'a'.repeat(32)].forEach(slug =>
            expect(AGENT_SLUG_PATTERN.test(slug)).toBe(true)
        )
    })

    test('invalid slugs', () => {
        ['', 'Claude', 'claude_code', 'claude.code', 'a b', 'a'.repeat(33)].forEach(slug =>
            expect(AGENT_SLUG_PATTERN.test(slug)).toBe(false)
        )
    })

    test('slug from the identifier', () => {
        expect(agentSlug({email: 'claude-code[agent]'})).toBe('claude-code')
    })

    test('last use of any token', () => {
        expect(agentLastUsed({
            tokens: [
                {name: 'ci', lastUsed: '2026-10-01T10:00:00'},
                {name: 'local', lastUsed: null},
                {name: 'laptop', lastUsed: '2026-10-05T08:00:00'},
            ]
        })).toBe('2026-10-05T08:00:00')
    })

    test('never used', () => {
        expect(agentLastUsed({tokens: [{name: 'ci', lastUsed: null}]})).toBeUndefined()
        expect(agentLastUsed({tokens: []})).toBeUndefined()
    })
})

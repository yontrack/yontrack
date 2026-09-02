import fs from "fs";
import path from "path";

/**
 * The pulse is the codebase's first animation, and the `prefers-reduced-motion`
 * guard around it is the pattern every later animation is meant to copy. That
 * guard lives in CSS, where jsdom will not evaluate it, so this test reads the
 * stylesheet rather than a rendered component - the assertion is about the rule,
 * not about what a headless DOM happens to compute.
 */
describe('reduced motion', () => {

    const css = fs.readFileSync(
        path.join(process.cwd(), 'styles', 'globals.css'),
        'utf-8',
    )

    const pulseRule = /@media\s*\(prefers-reduced-motion:\s*no-preference\)\s*\{[^}]*\.ot-pulse\s*\{[^}]*animation:[^}]*\}[^}]*\}/

    it('declares the pulse animation only inside a no-preference guard', () => {
        expect(css).toMatch(pulseRule)
    })

    it('has no unguarded .ot-pulse animation', () => {
        // Everything outside the guarded block must be free of a `.ot-pulse`
        // rule: an unguarded one would win by source order and defeat the guard.
        const withoutGuardedRule = css.replace(pulseRule, '')
        expect(withoutGuardedRule).not.toMatch(/\.ot-pulse\s*\{/)
    })

    it('defines the keyframes the guarded rule refers to', () => {
        expect(css).toMatch(/@keyframes\s+ot-pulse\s*\{/)
    })
})

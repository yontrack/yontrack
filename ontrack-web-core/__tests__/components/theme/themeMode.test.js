import {
    DEFAULT_THEME_MODE,
    normalizeThemeMode,
    resolveTheme,
    THEME_MODES,
    toServerThemeMode,
} from "@components/theme/themeMode"

describe('normalizeThemeMode', () => {

    it.each(THEME_MODES)('keeps a known mode as-is: %s', (mode) => {
        expect(normalizeThemeMode(mode)).toEqual(mode)
    })

    it('accepts the uppercase form used by the GraphQL enum', () => {
        expect(normalizeThemeMode('DARK')).toEqual('dark')
        expect(normalizeThemeMode('LIGHT')).toEqual('light')
        expect(normalizeThemeMode('SYSTEM')).toEqual('system')
    })

    it.each([null, undefined, '', 'purple', 42, {}])(
        'falls back to the default for an unusable value: %p',
        (value) => {
            expect(normalizeThemeMode(value)).toEqual(DEFAULT_THEME_MODE)
        }
    )

    it('defaults new users to following the system', () => {
        expect(DEFAULT_THEME_MODE).toEqual('system')
    })
})

describe('toServerThemeMode', () => {

    it('converts to the uppercase GraphQL enum value', () => {
        expect(toServerThemeMode('dark')).toEqual('DARK')
        expect(toServerThemeMode('light')).toEqual('LIGHT')
        expect(toServerThemeMode('system')).toEqual('SYSTEM')
    })

    it('normalizes before converting, so it can never emit an invalid enum value', () => {
        expect(toServerThemeMode('nonsense')).toEqual('SYSTEM')
    })
})

describe('resolveTheme', () => {

    it('resolves an explicit mode regardless of the system preference', () => {
        expect(resolveTheme('light', true)).toEqual('light')
        expect(resolveTheme('light', false)).toEqual('light')
        expect(resolveTheme('dark', true)).toEqual('dark')
        expect(resolveTheme('dark', false)).toEqual('dark')
    })

    it('follows the system preference in system mode', () => {
        expect(resolveTheme('system', true)).toEqual('dark')
        expect(resolveTheme('system', false)).toEqual('light')
    })

    it('resolves to light when the system preference is unknown', () => {
        // Server-side rendering has no `prefers-color-scheme` to read.
        expect(resolveTheme('system', undefined)).toEqual('light')
    })

    it('resolves an unusable mode through the default', () => {
        expect(resolveTheme('nonsense', true)).toEqual('dark')
    })
})

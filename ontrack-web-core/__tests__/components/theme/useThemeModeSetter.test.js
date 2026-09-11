import "@testing-library/jest-dom"
import {act, render, screen} from "@testing-library/react"
import ThemeProvider, {useTheme} from "@components/providers/ThemeProvider"
import {PreferencesContext} from "@components/providers/PreferencesProvider"
import {MessageContext} from "@components/providers/MessageProvider"
import {getStoredThemeMode} from "@components/theme/themeStorage"
import {useThemeModeSetter} from "@components/theme/useThemeModeSetter"

/**
 * The dual write, covered once.
 *
 * Both UIs write the theme through this hook - the desktop `ThemeSwitch` and the
 * mobile row - so the two halves it writes, and the failure path between them,
 * are tested here rather than twice over on each caller.
 */

const stubMatchMedia = (dark) => {
    window.matchMedia = jest.fn().mockImplementation(query => ({
        media: query,
        matches: dark,
        addEventListener: () => {
        },
        removeEventListener: () => {
        },
    }))
}

const clearCookies = () => {
    document.cookie.split(';').forEach(c => {
        const name = c.split('=')[0].trim()
        if (name) document.cookie = `${name}=; max-age=0; path=/`
    })
}

/** Exposes the setter as a button, and the resolved theme beside it. */
function Probe({mode}) {
    const setThemeMode = useThemeModeSetter()
    const {resolvedTheme} = useTheme()
    return (
        <>
            <button data-testid="set" onClick={() => setThemeMode(mode)}>set</button>
            <span data-testid="resolved">{resolvedTheme}</span>
        </>
    )
}

const renderProbe = ({
                         mode = 'dark',
                         setPreferences = jest.fn(),
                         messageApi = {warning: jest.fn()},
                     } = {}) => {
    render(
        <ThemeProvider>
            <MessageContext.Provider value={{messageApi}}>
                <PreferencesContext.Provider value={{setPreferences, loaded: true}}>
                    <Probe mode={mode}/>
                </PreferencesContext.Provider>
            </MessageContext.Provider>
        </ThemeProvider>
    )
    return {setPreferences, messageApi}
}

const set = () => act(() => screen.getByTestId('set').click())
const resolved = () => screen.getByTestId('resolved').textContent

describe('useThemeModeSetter', () => {

    beforeEach(() => {
        clearCookies()
        document.documentElement.removeAttribute('data-theme')
        stubMatchMedia(false)
    })

    afterEach(clearCookies)

    it('applies the choice immediately, without waiting for the server', () => {
        renderProbe({mode: 'dark'})
        expect(resolved()).toEqual('light')
        set()
        expect(resolved()).toEqual('dark')
        expect(document.documentElement.getAttribute('data-theme')).toEqual('dark')
    })

    it('mirrors the choice locally, so the next first paint does not flash', () => {
        renderProbe({mode: 'dark'})
        set()
        expect(getStoredThemeMode()).toEqual('dark')
    })

    it('persists the choice server-side, so it follows the user to their other devices', () => {
        const {setPreferences} = renderProbe({mode: 'dark'})
        set()
        // Uppercase: the GraphQL `ThemeMode` enum form.
        expect(setPreferences).toHaveBeenCalledWith({themeMode: 'DARK'})
    })

    it('persists system mode too, rather than treating it as "unset"', () => {
        const {setPreferences} = renderProbe({mode: 'system'})
        set()
        expect(setPreferences).toHaveBeenLastCalledWith({themeMode: 'SYSTEM'})
        expect(getStoredThemeMode()).toEqual('system')
    })

    describe('when the server rejects the change', () => {

        const failing = () => Promise.reject(new Error('backend down'))

        it('keeps the theme the user just picked rather than snapping it back', async () => {
            renderProbe({setPreferences: jest.fn(failing)})
            set()
            await act(async () => {
            })
            expect(resolved()).toEqual('dark')
        })

        it('says so, instead of failing silently', async () => {
            // The cookie now disagrees with the server: on the next sign-in the
            // server wins and the choice vanishes. The user has to be told - and
            // told what they actually lose, which is the choice following them
            // to their phone.
            const {messageApi} = renderProbe({setPreferences: jest.fn(failing)})
            set()
            await act(async () => {
            })
            expect(messageApi.warning).toHaveBeenCalledWith(
                "Theme applied, but not saved to your profile — it may not follow you to other devices."
            )
        })

        it('does not leave an unhandled rejection behind', async () => {
            const onUnhandled = jest.fn()
            process.on('unhandledRejection', onUnhandled)
            try {
                renderProbe({setPreferences: jest.fn(failing)})
                set()
                await act(async () => {
                })
                // A macrotask: long enough for an unhandled rejection to surface.
                await new Promise(resolve => setTimeout(resolve, 0))
                expect(onUnhandled).not.toHaveBeenCalled()
            } finally {
                process.off('unhandledRejection', onUnhandled)
            }
        })

        it('survives having no message channel at all', async () => {
            // The hook is called from two provider stacks; neither guarantees a
            // message API is mounted above it, and a theme choice must not throw
            // because the warning has nowhere to go.
            renderProbe({setPreferences: jest.fn(failing), messageApi: null})
            set()
            await act(async () => {
            })
            expect(resolved()).toEqual('dark')
        })
    })
})

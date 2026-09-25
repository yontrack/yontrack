import {createContext, useCallback, useContext, useEffect, useRef, useState} from "react";
import CommandPalette from "@components/search/palette/CommandPalette";

/**
 * Opening of the command palette from anywhere in the desktop UI (#1884).
 */
export const CommandPaletteContext = createContext({
    open: false,
    openPalette: () => {
    },
})

export const useCommandPalette = () => useContext(CommandPaletteContext)

/**
 * Whether the target of a key event takes text: `/` typed there is text, not a shortcut.
 */
export function isEditableTarget(target) {
    if (!target || typeof target.closest !== 'function') return false
    return !!target.closest('input, textarea, select, [contenteditable=""], [contenteditable="true"]')
        || target.isContentEditable === true
}

/**
 * Whether a key event anywhere in the page asks for the palette:
 *
 * - ⌘K / Ctrl+K from anywhere, focused inputs included;
 * - `/` when nothing taking text is focused.
 */
export function isPaletteShortcut(event) {
    if (event.altKey) return false
    const key = event.key?.toLowerCase()
    if (key === 'k') {
        return event.metaKey || event.ctrlKey
    }
    if (event.key === '/') {
        return !event.metaKey && !event.ctrlKey && !isEditableTarget(event.target)
    }
    return false
}

/**
 * Mounts the command palette and its keyboard shortcuts. Mounted by the desktop layout only: the
 * mobile UI has no global search (#1723).
 *
 * The palette is mounted only while open, so that each opening starts afresh. The focus goes back to
 * where it was when the palette was opened.
 */
export function CommandPaletteProvider({children}) {

    const [open, setOpen] = useState(false)
    const returnFocus = useRef(null)

    const openPalette = useCallback(() => {
        setOpen(wasOpen => {
            if (!wasOpen && typeof document !== 'undefined') {
                returnFocus.current = document.activeElement
            }
            return true
        })
    }, [])

    const closePalette = useCallback(() => setOpen(false), [])

    useEffect(() => {
        const onKeyDown = (event) => {
            if (isPaletteShortcut(event)) {
                event.preventDefault()
                openPalette()
            }
        }
        document.addEventListener('keydown', onKeyDown)
        return () => document.removeEventListener('keydown', onKeyDown)
    }, [openPalette])

    useEffect(() => {
        if (!open && returnFocus.current) {
            const element = returnFocus.current
            returnFocus.current = null
            element.focus?.({preventScroll: true})
        }
    }, [open])

    return (
        <CommandPaletteContext.Provider value={{open, openPalette}}>
            {children}
            {open && <CommandPalette onClose={closePalette}/>}
        </CommandPaletteContext.Provider>
    )
}

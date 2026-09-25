import {Button} from "antd";
import {FaSearch} from "react-icons/fa";
import {useCommandPalette} from "@components/search/palette/CommandPaletteContext";
import {useIsMacPlatform} from "@components/search/palette/platform";

/**
 * The "Search… ⌘K" button of the navigation bar, opening the command palette (#1884).
 *
 * It shows the shortcut of the platform - ⌘K on a Mac, Ctrl K elsewhere - and declares it with
 * `aria-keyshortcuts`, both working everywhere.
 */
export default function SearchPaletteButton() {
    const {openPalette} = useCommandPalette()
    const isMac = useIsMacPlatform()
    return (
        <Button
            className="ot-search-button"
            onClick={openPalette}
            aria-haspopup="dialog"
            aria-keyshortcuts={isMac ? 'Meta+K' : 'Control+K'}
            data-testid="search-button"
        >
            <FaSearch aria-hidden="true"/>
            <span className="ot-search-button-label">Search…</span>
            <kbd className="ot-kbd">{isMac ? '⌘K' : 'Ctrl K'}</kbd>
        </Button>
    )
}

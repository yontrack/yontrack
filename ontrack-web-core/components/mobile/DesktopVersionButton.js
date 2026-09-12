"use client"

/**
 * "Open the desktop version".
 *
 * The remember-then-navigate pair lives in `switchToDesktopUI`, which both this
 * button and the way back share; all this adds is the affordance.
 *
 * **One component and one label, on both surfaces.** The interstitial offers it
 * as the recommended way out of a dead end (#1730); the account screen offers it
 * as the deliberate door out of the mobile UI (#1733). Two components would be
 * two strings for one action, and two strings is how two surfaces drift into
 * saying different things about the same button.
 */

import {Button} from "antd"
import {switchToDesktopUI} from "@components/mobile/desktopPreference"

/**
 * @param {string} href The desktop page to land on.
 * @param {string} [type] antd button type - `primary` where it is the
 *   recommended action, the default `default` where it is one row among several.
 * @param {boolean} [block] Full width.
 * @param {string} [size] `large` by default: both callers are phone surfaces,
 *   and antd's default button is 32px, well under what a thumb wants. `large` is
 *   antd's biggest at 40px, and is what the account screen's sign out already
 *   asks for, so the two rows also match.
 */
export default function DesktopVersionButton({href, type = "default", block = false, size = "large"}) {
    return (
        <Button
            type={type}
            block={block}
            size={size}
            onClick={() => switchToDesktopUI(href)}
            data-testid="open-desktop-version"
        >
            Open the desktop version
        </Button>
    )
}

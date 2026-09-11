"use client"

/**
 * The dark / light control, on a phone.
 *
 * **A mobile component, not `ThemeSwitch`.** That one is desktop-shaped: it
 * hard-codes `id="theme-switch"`, which `theme.spec.js` locates by, and carries
 * a `stopPropagation` whose only purpose is stopping the desktop drawer from
 * closing. Reusing it would cross the boundary `doc/dev-guide/ui/mobile-ui.md`
 * draws - layouts are not shared.
 *
 * **The write is shared**, and deliberately: `useThemeModeSetter` is the one
 * place the cookie mirror and the `themeMode` preference are written together.
 * There is a single `themeMode` on the account, so choosing Dark here darkens
 * that user's desktop UI too, in every browser. The theme is a *user*
 * preference, not a device one - which is also why the control belongs on the
 * account screen rather than beside the `yontrack-ui=desktop` opt-out.
 *
 * **Three modes rather than a toggle.** `system` is `DEFAULT_THEME_MODE`, so it
 * is the state every user who has never picked is in; a binary switch could
 * neither show it nor offer a way back to it. The vocabulary also has to match
 * the desktop's, or a user who picked Auto there would find this control showing
 * something that is not true.
 *
 * **A caption, because a phone has no hover.** The desktop puts the only
 * explanation any mode ever gets inside a tooltip - "Follow the theme of your
 * operating system" is the one place Auto is defined - and on a touch screen
 * that text is unreachable. It is a line of secondary text here instead, present
 * in *every* mode: a caption that came and went would change the row's height as
 * the user taps across it, and sign out sits directly below.
 */

import {Segmented, Space, Typography} from "antd"
import {FaDesktop, FaMoon, FaSun} from "react-icons/fa"
import {useTheme} from "@components/providers/ThemeProvider"
import {useThemeModeSetter} from "@components/theme/useThemeModeSetter"

/**
 * What each mode means, in one line.
 *
 * Light and Dark are described as *overrides* rather than as descriptions, which
 * is the distinction that makes Auto comprehensible.
 *
 * @param {'light'|'dark'|'system'} mode What the user picked.
 * @param {'light'|'dark'} resolvedTheme What is actually being rendered.
 * @returns {string}
 */
export function themeModeCaption(mode, resolvedTheme) {
    switch (mode) {
        case 'light':
            return "Always light"
        case 'dark':
            return "Always dark"
        default:
            // Tracks the operating system live: `ThemeProvider` subscribes to
            // the `prefers-color-scheme` media query, so `resolvedTheme` - and
            // this line with it - changes without a reload.
            return `Auto — currently ${resolvedTheme}`
    }
}

export default function MobileThemeSwitch() {

    const {themeMode, resolvedTheme} = useTheme()
    const setThemeMode = useThemeModeSetter()

    const option = (value, label, icon) => ({
        value,
        label: (
            <Space size={4}>
                {icon}
                {label}
            </Space>
        ),
    })

    return (
        <Space direction="vertical" size={4} style={{width: '100%'}}>
            <Segmented
                block
                // `size="large"`: the segments are the tap targets, and antd's
                // default leaves them under the 44px a thumb wants.
                size="large"
                data-testid="mobile-theme-switch"
                value={themeMode}
                onChange={setThemeMode}
                options={[
                    option('light', "Light", <FaSun/>),
                    option('dark', "Dark", <FaMoon/>),
                    option('system', "Auto", <FaDesktop/>),
                ]}
            />
            <Typography.Text type="secondary" data-testid="mobile-theme-caption">
                {themeModeCaption(themeMode, resolvedTheme)}
            </Typography.Text>
        </Space>
    )
}

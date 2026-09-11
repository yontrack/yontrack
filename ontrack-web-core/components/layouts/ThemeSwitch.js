import {Segmented, Space, Tooltip} from "antd"
import {FaDesktop, FaMoon, FaSun} from "react-icons/fa"
import {useTheme} from "@components/providers/ThemeProvider"
import {useThemeModeSetter} from "@components/theme/useThemeModeSetter"

/**
 * The light / dark / system switch, shown in the user menu.
 *
 * The write itself - the local mirror, then the profile, then a warning when
 * only the first landed - lives in `useThemeModeSetter`, shared with the mobile
 * row on `/mobile/account`: there is one `themeMode` on the account and the two
 * UIs must not drift in how they write it.
 *
 * What stays here is the desktop's own shape: the tooltips, which a mouse can
 * reach and a phone cannot, and the contained click that keeps the drawer open.
 */
export default function ThemeSwitch() {

    const {themeMode} = useTheme()
    const setThemeMode = useThemeModeSetter()

    const option = (value, label, icon, help) => ({
        value,
        label: (
            <Tooltip title={help}>
                <Space size={4}>
                    {icon}
                    {label}
                </Space>
            </Tooltip>
        ),
    })

    return (
        <Segmented
            id="theme-switch"
            data-testid="theme-switch"
            // Stops the click from also closing the drawer through the menu's
            // own onClick handler.
            onClick={e => e.stopPropagation()}
            value={themeMode}
            onChange={setThemeMode}
            options={[
                option('light', "Light", <FaSun/>, "Always use the light theme"),
                option('dark', "Dark", <FaMoon/>, "Always use the dark theme"),
                option('system', "Auto", <FaDesktop/>, "Follow the theme of your operating system"),
            ]}
        />
    )
}

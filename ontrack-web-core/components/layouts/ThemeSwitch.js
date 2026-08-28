import {Segmented, Space, Tooltip} from "antd"
import {FaDesktop, FaMoon, FaSun} from "react-icons/fa"
import {useMessageApi} from "@components/providers/MessageProvider"
import {usePreferences} from "@components/providers/PreferencesProvider"
import {useTheme} from "@components/providers/ThemeProvider"
import {toServerThemeMode} from "@components/theme/themeMode"

/**
 * The light / dark / system switch, shown in the user menu.
 *
 * Writes to both halves of the persistence: the provider mirrors the choice into
 * a cookie so the next first paint is already right, and the server preference
 * makes the choice follow the user into another browser.
 *
 * This is the one place both are available - the theme provider sits above the
 * preferences in the tree, so it cannot persist to the server itself.
 */
export default function ThemeSwitch() {

    const {themeMode, setThemeMode} = useTheme()
    const preferences = usePreferences()
    const messageApi = useMessageApi()

    const onChange = async (mode) => {
        // Applied locally first: the switch must take effect immediately, not
        // after the mutation comes back.
        setThemeMode(mode)
        try {
            await preferences.setPreferences({themeMode: toServerThemeMode(mode)})
        } catch (error) {
            // The local choice stands - reverting it under the user mid-click
            // would be worse. But it must not fail silently: the cookie now
            // disagrees with the server, and on the next sign-in the server wins
            // and the choice quietly disappears.
            messageApi?.warning(
                "The theme was applied, but could not be saved to your profile. " +
                "It may not follow you to another browser."
            )
        }
    }

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
            onChange={onChange}
            options={[
                option('light', "Light", <FaSun/>, "Always use the light theme"),
                option('dark', "Dark", <FaMoon/>, "Always use the dark theme"),
                option('system', "Auto", <FaDesktop/>, "Follow the theme of your operating system"),
            ]}
        />
    )
}

import {theme, Typography} from "antd";

/**
 * Secondary text of the scorecard, which reads at WCAG AA in both themes.
 *
 * antd's `type="secondary"` is its tertiary colour - 45 % black, about 3.4:1 on white - which fails
 * AA for the small text of the tiles and cards. antd's secondary colour, 65 %, passes it.
 */
export default function SecondaryText({style, ...props}) {
    const {token} = theme.useToken()
    return <Typography.Text {...props} style={{color: token.colorTextSecondary, ...style}}/>
}

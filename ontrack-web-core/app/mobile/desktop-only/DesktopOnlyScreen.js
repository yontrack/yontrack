"use client"

/**
 * The interstitial: "that page is desktop only".
 *
 * A phone following a deep link - from a notification, from Slack - to a page
 * the mobile UI has no equivalent for has to land somewhere. The two easy
 * answers are both wrong. Silently serving the desktop page costs the user the
 * readable UI the redirect exists to give them; silently dropping them on the
 * mobile home loses what they came for.
 *
 * So: name the destination, and offer both ways out. Neither is taken for them.
 */

import Link from "next/link"
import {Alert, Space, Typography} from "antd"
import DesktopVersionButton from "@components/mobile/DesktopVersionButton"
import {describeDesktopRoute, MOBILE_HOME} from "@components/mobile/mobileRoutes"
import MobileScreen from "@components/mobile/layout/MobileScreen"

export default function DesktopOnlyScreen({target}) {

    // The path alone, without the query string, is what the route map knows.
    const pathname = target.split('?')[0]
    const description = describeDesktopRoute(pathname)

    return (
        <MobileScreen title="Desktop only">
            <Alert
                type="info"
                showIcon
                title={
                    <span data-testid="desktop-only-destination">
                        {
                            description ?
                                <>You were heading for <b>{description}</b>.</> :
                                <>You were heading for <Typography.Text code>{pathname}</Typography.Text>.</>
                        }
                    </span>
                }
                description="The mobile version does not have this page yet."
            />
            <Space orientation="vertical" size="small" style={{width: '100%'}}>
                <DesktopVersionButton href={target} type="primary" block/>
                {/*
                  A plain link, not a button: it goes nowhere special and
                  changes nothing, so it should look like the lesser of the two.
                */}
                <Typography.Paragraph style={{margin: 0, textAlign: 'center'}}>
                    <Link href={MOBILE_HOME} data-testid="desktop-only-home">
                        Go to the mobile home
                    </Link>
                </Typography.Paragraph>
            </Space>
            <Typography.Text type="secondary">
                Opening the desktop version keeps this device on it until you
                close your browser. The “Mobile version” entry in the desktop
                user menu brings you back sooner.
            </Typography.Text>
        </MobileScreen>
    )
}

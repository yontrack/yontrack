"use client"

/**
 * The promote and deploy entry points on the build screen.
 *
 * **Gated exactly as the desktop UI gates them**, off the build's own
 * `authorizations`: `build/promote` and `slotPipeline/create`. A user without
 * the right does not see the button rather than seeing one that fails - and the
 * second one is answered `false` on an instance with no environments licence,
 * so the deploy entry point disappears there without this file knowing anything
 * about licences.
 *
 * **What they do.** Promoting happens here, on the phone, in
 * `MobilePromoteSheet` (#1724). Deploying is still its own issue (#1725), and
 * until it lands that button switches this device to the desktop UI on the
 * build's own page, through `switchToDesktopUI` - the same cookie-then-navigate
 * pair the interstitial uses, and the same answer the initiative gives
 * everywhere it does not cover something yet. The caption below says so, and now
 * says it about the deploy button alone: telling a user who can only promote
 * that their promotion happens on the desktop stopped being true.
 *
 * @param {Object} build The build, with its `authorizations` and its branch.
 * @param {function} [onPromotion] Called once a promotion has actually been
 *   created, so the screen can refetch.
 */

import {useState} from "react"
import {Button, Space, Typography} from "antd"
import {FaRegThumbsUp, FaServer} from "react-icons/fa"
import {isAuthorized} from "@components/common/authorizations"
import {switchToDesktopUI} from "@components/mobile/desktopPreference"
import {desktopBuildUri} from "@components/mobile/mobileRoutes"
import MobilePromoteSheet from "@components/mobile/builds/MobilePromoteSheet"

export default function MobileBuildActions({build, onPromotion}) {

    const canPromote = isAuthorized(build, 'build', 'promote')
    const canDeploy = isAuthorized(build, 'slotPipeline', 'create')

    const [promoting, setPromoting] = useState(false)

    // Nothing to show a reader who can do neither - and no empty box either.
    if (!canPromote && !canDeploy) return null

    const openDesktop = () => switchToDesktopUI(desktopBuildUri(build.id))

    return (
        <Space direction="vertical" size="small" style={{width: '100%'}} data-testid="mobile-build-actions">
            <Space.Compact block>
                {
                    canPromote &&
                    <Button
                        block
                        type="primary"
                        icon={<FaRegThumbsUp/>}
                        data-testid="mobile-build-promote"
                        onClick={() => setPromoting(true)}
                    >
                        Promote
                    </Button>
                }
                {
                    canDeploy &&
                    <Button
                        block
                        icon={<FaServer/>}
                        data-testid="mobile-build-deploy"
                        onClick={openDesktop}
                    >
                        Deploy
                    </Button>
                }
            </Space.Compact>
            {
                canDeploy &&
                <Typography.Text type="secondary" className="ot-mobile-caption">
                    Deploying opens the desktop version for now.
                </Typography.Text>
            }
            {
                canPromote &&
                <MobilePromoteSheet
                    build={build}
                    open={promoting}
                    onClose={() => setPromoting(false)}
                    onPromoted={onPromotion}
                />
            }
        </Space>
    )
}

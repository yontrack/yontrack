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
 * **Both happen here now.** Promoting is `MobilePromoteSheet` (#1724) and
 * deploying is `MobileDeploySheet` (#1725); neither sends the device to the
 * desktop UI any more, and the caption which used to say so is gone with the
 * last thing it was true of.
 *
 * @param {Object} build The build, with its `authorizations` and its branch.
 * @param {function} [onPromotion] Called once a promotion has actually been
 *   created, so the screen can refetch.
 * @param {function} [onDeployment] Called with the new deployment's id once one
 *   has been created, so the screen can take the user to it.
 */

import {useState} from "react"
import {Button, Space} from "antd"
import {FaRegThumbsUp, FaServer} from "react-icons/fa"
import {isAuthorized} from "@components/common/authorizations"
import MobilePromoteSheet from "@components/mobile/builds/MobilePromoteSheet"
import MobileDeploySheet from "@components/mobile/builds/MobileDeploySheet"

export default function MobileBuildActions({build, onPromotion, onDeployment}) {

    const canPromote = isAuthorized(build, 'build', 'promote')
    const canDeploy = isAuthorized(build, 'slotPipeline', 'create')

    const [promoting, setPromoting] = useState(false)
    const [deploying, setDeploying] = useState(false)

    // Nothing to show a reader who can do neither - and no empty box either.
    if (!canPromote && !canDeploy) return null

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
                        onClick={() => setDeploying(true)}
                    >
                        Deploy
                    </Button>
                }
            </Space.Compact>
            {
                canPromote &&
                <MobilePromoteSheet
                    build={build}
                    open={promoting}
                    onClose={() => setPromoting(false)}
                    onPromoted={onPromotion}
                />
            }
            {
                canDeploy &&
                <MobileDeploySheet
                    build={build}
                    open={deploying}
                    onClose={() => setDeploying(false)}
                    onStarted={onDeployment}
                />
            }
        </Space>
    )
}

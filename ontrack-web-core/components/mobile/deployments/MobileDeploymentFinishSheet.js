"use client"

/**
 * Confirming the completion of a deployment, from a phone.
 *
 * **A confirmation and not a justification.** Completing a deployment is an
 * ordinary lifecycle step with fat-finger consequences - it is what feeds
 * `Build.currentDeployments`, the delivery map and every downstream `environment`
 * admission rule - but it bypasses nobody's control, so it earns a sheet naming
 * what is about to happen and no free-text field. The override sheet beside it
 * asks for a reason because an override is recorded *against* the user; this is
 * not that.
 *
 * The mutation itself stays on the screen rather than moving in here, so that a
 * refusal - `finishStatus.ok` false, which is how "Only the last pipeline can be
 * deployed." arrives - lands in the same place as a refused start. This sheet is
 * the question, not the action.
 *
 * @param {Object} deployment The deployment being completed - its slot and its
 *   build are what the question names.
 * @param {boolean} open Whether the sheet is up.
 * @param {boolean} running Whether the completion is in flight.
 * @param {function} onClose Close it, whatever the reason.
 * @param {function} onConfirm Go ahead.
 */

import {Button, Drawer, Space, Typography} from "antd"
import {slotNameWithoutProject} from "@components/extension/environments/SlotName"

export default function MobileDeploymentFinishSheet({deployment, open, running, onClose, onConfirm}) {

    const slot = deployment?.slot
    const build = deployment?.build

    return (
        <Drawer
            placement="bottom"
            height="auto"
            styles={{wrapper: {maxHeight: '85vh'}, content: {maxHeight: '85vh'}}}
            title="Complete the deployment"
            open={open}
            onClose={onClose}
        >
            <Space direction="vertical" size="middle" style={{width: '100%'}}>
                {/*
                  Which environment and which build, because a phone is often two
                  taps away from a different deployment and the sheet is the last
                  chance to notice.
                */}
                <Typography.Text data-testid="mobile-deployment-finish-confirm">
                    {`This marks the deployment of ${build?.displayName || build?.name || 'this build'} `}
                    {`into ${slot ? slotNameWithoutProject(slot) : 'this environment'} as done.`}
                </Typography.Text>

                <Space direction="vertical" size="small" style={{width: '100%'}}>
                    <Button
                        block
                        type="primary"
                        size="large"
                        loading={running}
                        data-testid="mobile-deployment-finish-submit"
                        onClick={onConfirm}
                    >
                        Complete the deployment
                    </Button>
                    {/*
                      "Not now" and not "Cancel": cancelling a deployment is a
                      different thing this screen also offers, and two buttons a
                      thumb apart must not read as the same word.
                    */}
                    <Button block size="large" onClick={onClose} data-testid="mobile-deployment-finish-close">
                        Not now
                    </Button>
                </Space>
            </Space>
        </Drawer>
    )
}
